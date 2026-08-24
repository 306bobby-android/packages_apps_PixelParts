#
# Copyright (C) 2023-2024 The Evolution X Project
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

# Torch strength control
#
# Replaces libcameraservice's weak CameraProviderExtension stubs so that
# CameraManager.turnOnTorchWithStrengthLevel(), and with it SystemUI's
# flashlight slider, work on a camera HAL that reports no torch strength
# range. The library reports itself unsupported when the flash LED nodes are
# absent, so this is inert on devices that do not have them.
$(call soong_config_set,libcameraservice,ext_lib,libcameraservice_ext_pixelparts)
