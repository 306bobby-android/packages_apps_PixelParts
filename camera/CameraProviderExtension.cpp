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

#include <algorithm>
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

// qcom,max-current for these torch LEDs. Used when max_brightness cannot be
// read at the moment cameraserver asks, which is not the same as the LEDs
// being absent - see maxLevel().
constexpr int32_t kFallbackMaxLevel = 500;

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
//
// This must not depend on the LEDs being probeable at the instant it is first
// called. CameraProviderManager::fixupTorchStrengthTags() runs exactly once,
// when the provider is enumerated, and bakes the answer into the camera's
// static metadata for the rest of the boot. The LED class devices come from
// leds-qpnp-flash-v2.ko, so a probe losing that race would silently disable
// torch strength until the next reboot - which is precisely the failure this
// shipped with. Fall back to the known range instead of giving up.
int32_t maxLevel() {
    static const int32_t sMaxLevel = [] {
        const int32_t fromNode = readInt(kMaxBrightnessNode, kUnsupportedLevel);
        if (fromNode > kUnsupportedLevel) {
            LOG(INFO) << "Torch strength control enabled, " << fromNode
                      << " levels (read from " << kMaxBrightnessNode << ")";
            return fromNode;
        }
        LOG(WARNING) << "Could not read " << kMaxBrightnessNode
                     << " (module not loaded yet?), assuming "
                     << kFallbackMaxLevel << " levels";
        return kFallbackMaxLevel;
    }();
    return sMaxLevel;
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
    // Deliberately not holding gLock across the writes below.
    // CameraProviderManager calls this with its own mInterfaceMutex held, so
    // blocking here blocks the whole camera service, and anything waiting on
    // it behind that. The lock guards gCurrentLevel and nothing else.
    if (!enabled) {
        writeNode(kSwitchNode, kSwitchOff);
        writeNode(kTorchNode0, "0");
        writeNode(kTorchNode1, "0");
        // gCurrentLevel is left alone: it is the level to use next time, and
        // cameraserver has already reset its own copy to the default.
        return;
    }

    const int32_t level = std::clamp(torchStrength, 1, maxLevel());
    const std::string value = std::to_string(level);

    writeNode(kTorchNode0, value);
    writeNode(kTorchNode1, value);

    // The switch node latches the per-LED currents. It has to be cycled for a
    // new brightness to take effect, including while the torch is already on.
    writeNode(kSwitchNode, kSwitchOff);
    writeNode(kSwitchNode, kSwitchOn);

    {
        std::lock_guard<std::mutex> lock(gLock);
        gCurrentLevel = level;
    }
}
