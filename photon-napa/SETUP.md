# NAPA on our Orange Pi: setup checklist

Hardware: Orange Pi 5 coprocessor, OV9281 cameras, roboRIO, WPILib 2026. No Jetson and no SystemCore needed.

NAPA is not a separate program. It is a library file (`/usr/lib/libphoton-napa.so`) that PhotonVision loads when
it starts. You install it once. After that you turn it on and off with a switch in the AprilTag pipeline.

## What to bring

- [ ] Laptop with this repo (branch `2026`)
- [ ] The jar: `photon-server\build\libs\photonvision-dev-v2026.7-1-g177076b9-linuxarm64.jar`
- [ ] An Ethernet cable
- [ ] A tape measure and a printed AprilTag (for the distance check)

## Part 1: install the library (needs internet)

The robot radio has no internet, and step 3 downloads the compiler. Do this part at home or at school with the
Orange Pi plugged into a router that has internet.

1. Connect the Orange Pi to the router with Ethernet and power it on. Check that the PhotonVision UI opens at
   <http://photonvision.local:5800>.

2. On the laptop, in PowerShell, copy this folder to the Orange Pi:

   ```powershell
   cd C:\Users\Eric\Documents\GitHub\Photonvision
   scp -r photon-napa pi@photonvision.local:~/
   ```

   `pi` is the login name and `photonvision.local` is the Orange Pi's network name. The password is usually
   `raspberry`. If that login fails, try user `orangepi` with password `orangepi`.

3. Log in to the Orange Pi and build the library:

   ```bash
   ssh pi@photonvision.local
   cat /proc/device-tree/model; echo        # should say Orange Pi 5 (or 5B / 5 Plus)
   sudo apt update
   sudo apt install -y build-essential openjdk-17-jdk-headless
   bash ~/photon-napa/build.sh --install
   ```

   Check the output for these three lines:

   - [ ] `building for aarch64 with: ... -mcpu=cortex-a76.cortex-a55 ...` (it found the Orange Pi 5's CPU)
   - [ ] `napa_test: all passed` (NAPA works on this board)
   - [ ] `installed /usr/lib/libphoton-napa.so`

   If there is a `FAIL` line or an error, stop here and save the full output.

4. Restart PhotonVision and log out:

   ```bash
   sudo systemctl restart photonvision
   exit
   ```

You only do Part 1 once. Updating the PhotonVision jar keeps the library. Reflashing the SD card or eMMC deletes
it, and then you redo Part 1.

## Part 2: update PhotonVision (no internet needed)

1. Open <http://photonvision.local:5800> and go to **Settings**.
2. **Export settings** first, so you have a backup.
3. Under **Offline Update**, pick `photonvision-dev-v2026.7-1-g177076b9-linuxarm64.jar`. Wait for PhotonVision to restart and the
   UI to come back.
4. In **Settings**, scroll to the legal notice at the bottom of the device card. It should credit Zachary Wilson for
   NAPA. If it does, the new jar is running.

## Part 3: turn on NAPA

1. **Calibration:** each OV9281 needs a calibration at the resolution you use (1280x800 is best for range). If a
   camera already has one, skip this. NAPA uses the same calibration as AprilTag 3, so you don't recalibrate for
   NAPA.
2. Open the camera's **AprilTag** pipeline and turn on **Detector: NAPA**.
   - If a red notice appears under the switch, the library didn't load. See Troubleshooting.
3. Start with these settings:

   | Setting | Value |
   | --- | --- |
   | Resolution | 1280x800 MJPEG |
   | Exposure | short (tags sharp while moving), raise gain to compensate |
   | NAPA Min Tag Size | 16 |
   | NAPA Max Error Bits | 1 |
   | NAPA Far Search | off (try it later if CPU runs high with several cameras) |

4. Repeat for each camera.

## Part 4: test NAPA against AprilTag 3

Put the tag at a measured distance (try 1 m, 3 m and as far away as it still detects). Fill in the table with
NAPA off and on. Latency is shown on the camera stream, and CPU is in Settings.

| Distance | Detector | Tags seen | Distance reading | Latency (ms) | CPU % |
| --- | --- | --- | --- | --- | --- |
| 1 m | AprilTag 3 | | | | |
| 1 m | NAPA | | | | |
| 3 m | AprilTag 3 | | | | |
| 3 m | NAPA | | | | |
| max | AprilTag 3 | | | | |
| max | NAPA | | | | |

What to expect:

- The distance readings agree within a few centimetres.
- NAPA uses less CPU, has lower latency, and still finds tags further away.
- The pose jitters less when the robot is still.
- With fast motion and long exposure, NAPA's corners can be off by a few pixels. Keep the exposure short and
  don't trust vision readings while the robot is moving fast.

Robot code doesn't change. The roboRIO gets the same PhotonLib results over NetworkTables.

## Troubleshooting

**Red notice "libphoton-napa isn't installed, so AprilTag 3 is still running" under the switch.** PhotonVision
didn't load the library. SSH in and check:

```bash
ls -l /usr/lib/libphoton-napa.so                         # must exist
sudo journalctl -u photonvision -b | grep -i napa         # shows why it failed to load
sudo systemctl restart photonvision
```

If the file is missing, redo Part 1 step 3.

**`sudo apt install` fails.** The Orange Pi has no internet. Plug it into a router that has internet, or send the
error to whoever has the repo, who can build `libphoton-napa.so` on another machine.

**`ssh` can't find `photonvision.local`.** Use the Orange Pi's IP address instead. You can find it in the router's
device list or in PhotonVision's Settings.

**Anything goes wrong at competition.** Turn **Detector: NAPA** off. PhotonVision goes straight back to AprilTag 3.
If the new jar itself is the problem, upload your old PhotonVision jar with Offline Update and import the settings
backup.
