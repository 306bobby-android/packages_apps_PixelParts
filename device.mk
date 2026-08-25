#
# Copyright (C) 2023-2024 The Evolution X Project
#               2024 crDroid Android Project
#
# SPDX-License-Identifier: Apache-2.0
#

# PixelParts app
PRODUCT_PACKAGES += \
    PixelParts

# PixelParts init rc
PRODUCT_PACKAGES += \
    init.pixelparts.rc

# PixelParts sepolicy
BOARD_SEPOLICY_DIRS += packages/apps/PixelParts/sepolicy

# Extra modem carrier configurations
#
# Adds carrier configs the stock modem image does not ship. mbn_sw.txt is
# replaced with one carrying every stock entry followed by these, and they are
# listed in oem_sw.txt as well. Stock's entries come through untouched, so
# every carrier that worked before still resolves. See mbn/README.md.
include packages/apps/PixelParts/mbn/mbn.mk

# Torch strength control
#
# Replaces libcameraservice's weak CameraProviderExtension stubs so that
# CameraManager.turnOnTorchWithStrengthLevel(), and with it SystemUI's
# flashlight slider, work on a camera HAL that reports no torch strength
# range. Enabling this is what makes the platform's flashlight strength UI
# appear, so only set it on a device whose flash LEDs are reachable through
# the led class devices the library writes.
$(call soong_config_set,libcameraservice,ext_lib,libcameraservice_ext_pixelparts)
