![example](https://raw.githubusercontent.com/Evolution-XYZ-Devices/packages_apps_PixelParts/udc/readme_resources/PixelParts.png)
extension for the one flashlight feature the camera HAL does not report.

## Current features

| Category | Feature | Description | QS Tile | Required kernel changes |
| --- | --- | --- | --- | --- |
| **Display** | `Automatic HBM` | Enable high brightness mode above an ambient light threshold | Automatic HBM | A writable `hbm_mode` backlight node |
### Torch brightness
Torch brightness is deliberately not an app feature. The camera HAL here
reports no `ANDROID_FLASH_INFO_STRENGTH_MAXIMUM_LEVEL`, so cameraserver
substitutes `1` and everything that consumes torch strength disables itself -
including SystemUI's own flashlight slider, which is otherwise ready to use.
supplies the strength range from the flash LED class devices instead.
`libcameraservice` declares those entry points weak and picks a replacement
through `soong_config_variable("libcameraservice", "ext_lib")`, which
own flashlight controls then work as they were written to, and
`CameraManager.turnOnTorchWithStrengthLevel()` works for any app.

## Including PixelParts

- Clone this repository to packages/apps/PixelParts directory in your AOSP build tree:

```
croot && git clone https://github.com/Evolution-X-Devices/packages_apps_PixelParts packages/apps/PixelParts
```

- Include the app during compilation by adding the following to device-*.mk:

[Commit 1/1 (device tree)](https://github.com/Evolution-X-Devices/device_google_bluejay/commit/6822dabe27de84fb7d52e85cb34d9a71c14d1112)

```
# PixelParts
include packages/apps/PixelParts/device.mk
```

This line includes the [device.mk](https://github.com/Evolution-XYZ-Devices/packages_apps_PixelParts/blob/udc/device.mk) file from the PixelParts repository, which will add the PixelParts application, its initialization script (init.rc), and the necessary security policies (sepolicies) to your AOSP build during compilation.
its sepolicy, and the camera provider extension.
| `/sys/class/backlight/panel0-backlight/hbm_mode` | Automatic HBM |
| `/sys/class/leds/led:torch_0/brightness` | Torch strength |
| `/sys/class/leds/led:torch_1/brightness` | Torch strength |
| `/sys/class/leds/led:switch_2/brightness` | Torch strength |
| `/sys/class/leds/led:torch_0/max_brightness` | Torch strength range |
[`init/init.pixelparts.rc`](init/init.pixelparts.rc) hands each node to the
uid that writes it, and [`sepolicy/`](sepolicy) labels the flash LEDs. Both

## Testing changes

- When testing new changes, it is much faster to compile the application standalone and update it manually rather than running a full AOSP build. Please note that some changes may require you to chmod 0666 sysfs nodes and set selinux to permissive. When compiling a full AOSP build, this is not needed assuming the init cmds and sepolicies have been properly configured.

Lunch your device and run the following cmd:

```
m PixelParts
```
- This also assumes you are already running an AOSP build including PixelParts as a priv-app in /system_ext.
linked into `libcameraservice`, so it needs `m libcameraservice` and a
cameraserver restart, or a full build.

## Credits

| Work                                                        | Author                                                                      |
| ----------------------------------------------------------- | --------------------------------------------------------------------------- |
| CustomSeekBar preference                                    | [Neobuddy89](https://forum.xda-developers.com/m/neobuddy89.3795148/)        |
