![example](readme_resources/PixelParts.png)

Device parts for redbull (Pixel 5, Pixel 4a 5G): a settings app for the one
panel feature the platform has no interface to, and a camera provider
extension for the one flashlight feature the camera HAL does not report.

## Current features

| Category | Feature | Description | QS Tile | Requires |
| --- | --- | --- | --- | --- |
| **Display** | `Automatic HBM` | Enable high brightness mode above an ambient light threshold | Automatic HBM | A writable `hbm_mode` backlight node |

### Torch brightness

Torch brightness is deliberately not an app feature. The camera HAL here
reports no `ANDROID_FLASH_INFO_STRENGTH_MAXIMUM_LEVEL`, so cameraserver
substitutes `1` and everything that consumes torch strength disables itself -
including SystemUI's own flashlight slider, which is otherwise ready to use.

[`camera/CameraProviderExtension.cpp`](camera/CameraProviderExtension.cpp)
supplies the strength range from the flash LED class devices instead.
`libcameraservice` declares those entry points weak and picks a replacement
through `soong_config_variable("libcameraservice", "ext_lib")`, which
[`device.mk`](device.mk) sets, so no ROM repository is patched. The platform's
own flashlight controls then work as they were written to, and
`CameraManager.turnOnTorchWithStrengthLevel()` works for any app.

The extension reports itself unsupported when the LED nodes are absent, so it
is inert on a device that does not have them.

## Including PixelParts

Clone this repository to `packages/apps/PixelParts` in your build tree:

```
croot && git clone https://github.com/306bobby-android/packages_apps_PixelParts packages/apps/PixelParts
```

Then add the following to your `device-*.mk`:

```
# PixelParts
include packages/apps/PixelParts/device.mk
```

That single line is the whole integration. It adds the app, its init script,
its sepolicy, and the camera provider extension.

## Device requirements

The sysfs paths are hardcoded for this platform's PMIC layout:

| Node | Used by |
| --- | --- |
| `/sys/class/backlight/panel0-backlight/hbm_mode` | Automatic HBM |
| `/sys/class/leds/led:torch_0/brightness` | Torch strength |
| `/sys/class/leds/led:torch_1/brightness` | Torch strength |
| `/sys/class/leds/led:switch_2/brightness` | Torch strength |
| `/sys/class/leds/led:torch_0/max_brightness` | Torch strength range |

[`init/init.pixelparts.rc`](init/init.pixelparts.rc) hands each node to the
uid that writes it, and [`sepolicy/`](sepolicy) labels the flash LEDs. Both
carry the physical device paths, because init will not chown through the
`/sys/class` symlinks.

## Testing changes

Building the app alone is much quicker than a full build:

```
m PixelParts
```

Changes to the camera provider extension are not covered by that - it is
linked into `libcameraservice`, so it needs `m libcameraservice` and a
cameraserver restart, or a full build.

## Credits

| Work | Author |
| --- | --- |
| CustomSeekBar preference | [Neobuddy89](https://forum.xda-developers.com/m/neobuddy89.3795148/) |
| Tile handler activity | [iusmac](https://github.com/iusmac) |
| CameraProviderExtension hook | [The LibreMobileOS Foundation](https://github.com/libremobileos) |
