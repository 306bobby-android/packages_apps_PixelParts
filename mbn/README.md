# Extra modem carrier configurations

Carrier configurations (`mcfg_sw.mbn`) the stock redbull modem image does not
ship, for carriers that were never certified for this device.

## Why these are indexed separately

`vendor/google/redfin` ships `mcfg_sw/` with two files that describe the tree:

| File | Role |
| --- | --- |
| `mbn_sw.txt` | index of the 116 stock configurations |
| `mbn_sw.dig` | 32 byte digest over that set |

The obvious way to add configurations - drop the files in and edit
`mbn_sw.txt` - breaks every carrier, because the index and the digest stop
describing the tree they name. That is what happens if you take the Magisk
module apart and copy its payload into vendor: the module works by shipping a
Magisk `.replace` marker, which masks the stock directory outright and
substitutes a complete, self-consistent tree of its own. Of the 117 stock
configurations, exactly one survives that swap.

`strings` on `vendor/google/redfin/radio/modem.img` shows the modem reads a
third path:

```
/mcfg_sw/mbn_sw.txt
/mcfg_sw/mbn_sw.dig
/mcfg_sw/oem_sw.txt      <- a second, independent index
mcfg_autoselect_by_uim
```

Stock ships no `oem_sw.txt`, and there is no `oem_sw.dig` to keep in step. So
everything here is indexed in `oem_sw.txt` alone and `mbn_sw.txt` /
`mbn_sw.dig` are left byte for byte as they shipped. Stock carriers keep
resolving exactly as before; these are additional.

Paths in `oem_sw.txt` are written `mcfg_sw/<path>` and resolve against
`mcfg_sw/generic/`, matching the layout the module ships.

## Regenerating

`mbn.mk` and `oem_sw.txt` are both generated from the contents of
`mcfg_sw/generic/`. After adding or removing a configuration:

```
cd packages/apps/PixelParts/mbn && ./generate.py
```

Never edit either by hand. An index that disagrees with the tree it names is
the whole reason the stock `mbn_sw.txt` is left alone.

## The modem caches its choice

Adding a configuration is not enough on its own. The modem records which mcfg
it selected, and the RIL caches carrier state in `/data/vendor/radio`
(`qcril.db`, `iccid_0`), so a configuration that was not present when the SIM
was first seen is not picked up by itself. The Magisk module handles this by
deleting those paths from its installer.

A clean flash has the same effect, since that data is wiped. A dirty flash
does not. Rather than reach into another process's data directory, PixelParts
exposes `TelephonyManager.rebootModem()` as **Restart modem** on the IMS
screen, which makes the modem run selection again.

## Provenance and caveats

These are Google and Qualcomm signed carrier configurations, taken from the
payload of https://github.com/stanislawrogasik/Pixel5-VoLTE-VoWiFi. Only the
`generic/Pixel/*` set is included - that repository also carries Nokia 8.3 5G
configurations for manual QPST flashing, which are for different modem
hardware and are not shipped here. Redistributing signed carrier
configurations has licensing implications worth understanding before
publishing a build with them.

A configuration built against a different modem baseline may be rejected by
the modem, and forcing IMS features onto an uncertified network changes how
emergency calls are routed - see the warning on the PixelParts IMS screen.
