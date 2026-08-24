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
# Adds carrier configs the stock modem image does not ship, indexed only by
# oem_sw.txt, which the modem reads as a second index alongside mbn_sw.txt.
# Stock's mbn_sw.txt and mbn_sw.dig are left untouched, so every carrier that
# worked before still resolves. See mbn/README.md.
include packages/apps/PixelParts/mbn/mbn.mk

# Torch strength control
#
# Replaces libcameraservice's weak CameraProviderExtension stubs so that
# CameraManager.turnOnTorchWithStrengthLevel(), and with it SystemUI's
# flashlight slider, work on a camera HAL that reports no torch strength
# range. The library reports itself unsupported when the flash LED nodes are
# absent, so this is inert on devices that do not have them.
$(call soong_config_set,libcameraservice,ext_lib,libcameraservice_ext_pixelparts)
