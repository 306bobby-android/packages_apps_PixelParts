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

Both indexes are used here.

`oem_sw.txt` lists these 343 configurations. Stock ships no `oem_sw.txt` and
there is no `oem_sw.dig` to keep in step, so this costs nothing.

`mbn_sw.txt` replaces the stock copy with every stock entry, in its original
order, followed by these. 460 entries, none dangling - more coverage than
stock's 117 or the module's 81, of which 17 point at files that do not exist.
Replacing it is what the module does; carrying the stock entries through is
what the module does not, and is why this does not cost you the carriers that
already worked.

`mbn_sw.dig` is left as stock's. The digest is advisory rather than enforced:
the module ships a digest from October 2020 against an index from March 2021
that has 17 dangling entries, and works regardless. A stale digest makes the
modem rescan, which is the wanted behaviour anyway.

The two indexes use different path forms and are not interchangeable -
`mbn_sw.txt` entries include `generic/`, `oem_sw.txt` entries are resolved
against `mcfg_sw/generic/` and do not.

The override is prepended to `PRODUCT_COPY_FILES`, because a duplicate
destination resolves in favour of the first entry and the rest are silently
dropped. After a build, confirm the stock copy is the one that lost:

```
grep mbn_sw.txt $OUT/product_copy_files_ignored.txt
```

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
