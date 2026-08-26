/*
 * Copyright (C) 2024 The Evolution X Project
 *               2024 crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

// Torch strength through the flash LED class devices, which the camera HAL
// does not expose: it reports no ANDROID_FLASH_INFO_STRENGTH_MAXIMUM_LEVEL, so
// SystemUI's slider stays hidden. These symbols are weak in libcameraservice;
// soong_config_set(libcameraservice, ext_lib, ...) links this in over them.

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

// The value the HAL itself writes. 0 or 255 wedges the flash until reboot.
constexpr const char kSwitchOn[] = "1";

constexpr int32_t kUnsupportedLevel = 1;

// qcom,max-current, used when max_brightness cannot be read yet.
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

// CameraProviderManager::fixupTorchStrengthTags() calls this once at provider
// enumeration and bakes the result into static metadata for the whole boot,
// which can be before leds-qpnp-flash-v2.ko has probed. Fall back to the known
// range rather than lose that race and report unsupported until reboot.
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
    // False when the LED nodes are missing, so this stays harmless elsewhere.
    return maxLevel() > kUnsupportedLevel;
}

int32_t getTorchDefaultStrengthLevelExt() {
    return maxLevel();
}

int32_t getTorchMaxStrengthLevelExt() {
    return maxLevel();
}

int32_t getTorchStrengthLevelExt() {
    std::lock_guard<std::mutex> lock(gLock);
    // Never 0: the framework treats the level as 1-based.
    return gCurrentLevel > 0 ? gCurrentLevel : getTorchDefaultStrengthLevelExt();
}

void setTorchStrengthLevelExt(int32_t torchStrength, bool enabled) {
    // gLock guards gCurrentLevel only. CameraProviderManager holds its
    // mInterfaceMutex across this call, so the writes stay outside the lock.
    if (!enabled) {
        // The HAL turns the LEDs off itself. Zeroing the currents would latch
        // the channel off at the next enable, and writing the switch at all
        // wedges the flash.
        return;
    }

    const int32_t level = std::clamp(torchStrength, 1, maxLevel());
    const std::string value = std::to_string(level);

    writeNode(kTorchNode0, value);
    writeNode(kTorchNode1, value);

    // The driver latches the currents when the switch is asserted rather than
    // tracking them live, so re-asserting is what applies a level while lit.
    writeNode(kSwitchNode, kSwitchOn);

    {
        std::lock_guard<std::mutex> lock(gLock);
        gCurrentLevel = level;
    }
}
