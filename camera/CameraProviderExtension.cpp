/*
 * Copyright (C) 2024 The Evolution X Project
 *               2024 crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

// Torch strength control for flash LEDs that the camera HAL does not expose.
//
// The QCOM camera HAL on this platform reports no
// ANDROID_FLASH_INFO_STRENGTH_MAXIMUM_LEVEL, so cameraserver fills in 1 and
// every consumer of torch strength switches itself off - including SystemUI's
// flashlight slider and the percentage subtitle on its tile. The LEDs are
// perfectly capable of it, they are just only reachable through their led
// class devices.
//
// These five symbols are declared weak in libcameraservice
// (common/CameraProviderExtension.cpp). Naming this library in
// soong_config_set(libcameraservice, ext_lib, ...) puts it in
// libcameraservice's whole_static_libs, where these strong definitions win
// over the weak defaults and CameraProviderManager routes torch strength here
// instead of to the HAL.

#define LOG_TAG "CameraProviderExtensionPixelParts"

#include "common/CameraProviderExtension.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/strings.h>

#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <mutex>
#include <string>

using ::android::base::ParseInt;
using ::android::base::ReadFileToString;
using ::android::base::Trim;
using ::android::base::WriteStringToFile;

namespace {

constexpr const char kTorchNode0[] = "/sys/class/leds/led:torch_0/brightness";
constexpr const char kTorchNode1[] = "/sys/class/leds/led:torch_1/brightness";
constexpr const char kSwitchNode[] = "/sys/class/leds/led:switch_2/brightness";
constexpr const char kMaxBrightnessNode[] = "/sys/class/leds/led:torch_0/max_brightness";

// The switch node is a boolean latch - qpnp_flash_led_brightness_set() only
// looks at "value > 0" - and its led class device declares no max_brightness,
// so the LED core clamps it to LED_FULL.
constexpr const char kSwitchOn[] = "255";
constexpr const char kSwitchOff[] = "0";

constexpr int32_t kUnsupportedLevel = 1;

std::mutex gLock;
int32_t gCurrentLevel = 0;  // guarded by gLock

int32_t readInt(const char* path, int32_t defaultValue) {
    std::string content;
    if (!ReadFileToString(path, &content, true)) {
        return defaultValue;
    }
    int32_t value;
    if (!ParseInt(Trim(content), &value)) {
        LOG(WARNING) << "Unexpected contents in " << path << ": " << Trim(content);
        return defaultValue;
    }
    return value;
}

// Highest value the torch LEDs accept. The driver takes this from
// qcom,max-current in the PMIC device tree, so it is a real current limit and
// not the 255 an led class device otherwise defaults to.
int32_t maxLevel() {
    // Resolved lazily and only cached once it succeeds: cameraserver can reach
    // us before leds-qpnp-flash-v2.ko has finished loading, and a negative
    // result must not be allowed to stick.
    static std::atomic<int32_t> sMaxLevel{kUnsupportedLevel};

    int32_t cached = sMaxLevel.load();
    if (cached > kUnsupportedLevel) {
        return cached;
    }

    if (access(kMaxBrightnessNode, R_OK) != 0 || access(kSwitchNode, F_OK) != 0) {
        return kUnsupportedLevel;
    }

    const int32_t resolved = readInt(kMaxBrightnessNode, kUnsupportedLevel);
    if (resolved <= kUnsupportedLevel) {
        LOG(WARNING) << "Flash LEDs report no usable brightness range";
        return kUnsupportedLevel;
    }

    sMaxLevel.store(resolved);
    LOG(INFO) << "Torch strength control enabled, " << resolved << " levels";
    return resolved;
}

void writeNode(const char* path, const std::string& value) {
    if (!WriteStringToFile(value, path, true)) {
        PLOG(ERROR) << "Failed to write " << value << " to " << path;
    }
}

}  // namespace

bool supportsTorchStrengthControlExt() {
    // Reported as unsupported when the LED nodes are missing, so that this
    // library stays harmless on any device that picks it up without the
    // matching flash LED layout.
    return maxLevel() > kUnsupportedLevel;
}

int32_t getTorchDefaultStrengthLevelExt() {
    // cameraserver applies this whenever the torch is switched on without an
    // explicit level, so it is what a plain setTorchMode() gets.
    return maxLevel();
}

int32_t getTorchMaxStrengthLevelExt() {
    return maxLevel();
}

int32_t getTorchStrengthLevelExt() {
    std::lock_guard<std::mutex> lock(gLock);
    // Never 0: the framework treats the strength level as 1-based, and this is
    // read before anything has been set.
    return gCurrentLevel > 0 ? gCurrentLevel : getTorchDefaultStrengthLevelExt();
}

void setTorchStrengthLevelExt(int32_t torchStrength, bool enabled) {
    const int32_t max = maxLevel();
    if (max <= kUnsupportedLevel) {
        return;
    }

    std::lock_guard<std::mutex> lock(gLock);

    if (!enabled) {
        writeNode(kSwitchNode, kSwitchOff);
        writeNode(kTorchNode0, "0");
        writeNode(kTorchNode1, "0");
        // Left as the level to use next time; cameraserver has already reset
        // its own copy to the default.
        return;
    }

    const int32_t level = std::clamp(torchStrength, 1, max);
    const std::string value = std::to_string(level);

    writeNode(kTorchNode0, value);
    writeNode(kTorchNode1, value);

    // The switch node latches the per-LED currents. It has to be cycled for a
    // new brightness to take effect, including while the torch is already on.
    writeNode(kSwitchNode, kSwitchOff);
    writeNode(kSwitchNode, kSwitchOn);

    gCurrentLevel = level;
}
