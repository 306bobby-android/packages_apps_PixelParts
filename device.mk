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

# Extra modem carrier configurations. Stock's entries come through untouched.
# See mbn/README.md.
include packages/apps/PixelParts/mbn/mbn.mk
