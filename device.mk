#
# Copyright (C) 2023-2024 The Evolution X Project
#
# SPDX-License-Identifier: Apache-2.0
#

# PixelParts app
PRODUCT_PACKAGES += \
    PixelParts

    init.pixelparts.rc
# PixelParts sepolicy
BOARD_SEPOLICY_DIRS += packages/apps/PixelParts/sepolicy
# Torch strength control. Replaces libcameraservice's weak
# CameraProviderExtension stubs, which is what surfaces SystemUI's flashlight
# slider. Only for devices whose flash LEDs are led class devices.
$(call soong_config_set,libcameraservice,ext_lib,libcameraservice_ext_pixelparts)
