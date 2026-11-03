# photon-napa: the NAPA AprilTag detector for PhotonVision

[NAPA](https://github.com/zwatx/NAPA), by **Zachary Wilson**, is a CPU AprilTag detector that samples a sparse grid
of pixels and only does full-resolution work on the tags themselves. In NAPA's own benchmark it is about 25x faster
than AprilTag with WPILib's defaults and finds more tags. This directory builds it as `libphoton-napa.so`, which the
AprilTag pipeline uses when you turn on **Detector: NAPA**.

The port comes from Spectrum 3847's PhotonVision fork
([`spectrum-2027`](https://github.com/Spectrum3847/photonvision/tree/spectrum-2027), commit `b2700c55`, "NAPA CPU
AprilTag detector (photonvision-101)") and its native side from
[SpectrumJetson](https://github.com/Spectrum3847/SpectrumJetson) (`detector/napa/`, `patches/napa-*.patch`). Spectrum
runs it on a Jetson; this copy is for CPU-only coprocessors such as the Orange Pi 5.

## Licence and credit

NAPA is GPL-3.0-only, with an extra term under GPL section 7(b): copies and works based on NAPA must keep its notice
and credit Zachary Wilson as NAPA's author in their Appropriate Legal Notices. PhotonVision does this in the Settings
page's device card and in `ExternalLicenses/NAPA-License`. Keep both if you change the UI. `napa_far.*`,
`napa_test.cc` and the patches are from SpectrumJetson (GPL-3.0).

## Layout

| Path | What it is |
| --- | --- |
| `third_party/napa/` | NAPA v0.5.1 (commit `de613ad`), `src/napa_vision/_native/` plus `LICENSE`, `NOTICE`, `THIRD_PARTY_NOTICES`, unmodified |
| `patches/` | SpectrumJetson's NAPA patches, applied in name order at build time: early-decode slack, scan speed-ups, and a streaming API that `napa_far.cc` links against (unused here). None of them changes what whole-frame detection returns. |
| `src/napa_far.{h,cc}` | One detector per camera, with an optional far search (see below). Verbatim from SpectrumJetson. |
| `src/NapaDetectorJNI.cc` | The JNI bridge for `org.photonvision.jni.NapaDetectorJNI`. Spectrum's, without streaming NAPA (see below). |
| `src/napa_test.cc` | Self-test, run by `build.sh`: renders tags and checks IDs, corner order and convention, homography and far search. |
| `build.sh` | Builds the library on the coprocessor, runs the self-test, and with `--install` installs it. |

## Install on an Orange Pi (or any Linux coprocessor)

The library is built on the coprocessor itself (aarch64 or x86_64 Linux). It needs gcc/g++, `patch`, and a JDK's
`jni.h`. It does not need OpenCV, WPILib or CUDA.

1. Copy this directory to the coprocessor, for example from the repository root:

   ```bash
   scp -r photon-napa <user>@photonvision.local:~/
   ```

   You can also clone your fork on the coprocessor.

2. On the coprocessor:

   ```bash
   sudo apt update
   sudo apt install -y build-essential openjdk-17-jdk-headless   # any JDK works; it only provides jni.h
   bash ~/photon-napa/build.sh --install
   sudo systemctl restart photonvision
   ```

   The self-test should end with no `FAIL` lines. The library goes to `/usr/lib/libphoton-napa.so`, which is on
   Java's default `java.library.path`. On an RK3588 or RK3588S (Orange Pi 5, 5B, 5 Plus, 5 Pro) the build tunes for
   its Cortex-A76 and A55 cores. To choose other flags, set `ARCH_FLAGS`.

3. In the PhotonVision UI, open the camera's AprilTag pipeline and turn on **Detector: NAPA**. If the library isn't
   installed, a red notice appears under the switch and the pipeline keeps running AprilTag 3. The log also says
   why: look for `NAPA detector selected but libphoton-napa isn't installed`.

Reflashing the coprocessor image removes `/usr/lib/libphoton-napa.so`. Updating the PhotonVision jar does not.

## Settings

When NAPA is on, the AprilTag tab hides AprilTag 3's decimate, blur, threads and refine edges, which don't apply to
it. NAPA always works on the full-resolution frame.

| Setting | Default | Meaning |
| --- | --- | --- |
| NAPA Min Tag Size (px) | 16 | Smallest tag NAPA is sure to find, measured as the black square's side in pixels. 32 (NAPA's own default) is about 5x cheaper but misses small, far tags. Spectrum measured that 16 finds about 13% more tags than their GPU detector on field recordings. Hidden and unused while Far Search is on. |
| NAPA Max Error Bits | 1 | Code bits NAPA may correct; tags corrected more are dropped. Replaces AprilTag 3's hamming filter. For 36h11, 2-3 find a few more tags but risk wrong IDs. For 16h5 use 0 (at most 2): its codes are close together. |
| NAPA Far Search | off | Searches the whole frame at 32 px every frame (in place of *Min Tag Size*), then looks for tags down to *Far Min Tag Size* only around small tags (under 48 px) seen in the last 0.5 s and in one of four horizontal bands of the frame at a time (at most 60 bands a second). This is cheaper than a 16 px minimum everywhere, which helps with several cameras on one board. |
| NAPA Far Min Tag Size (px) | 16 | Smallest tag searched in the far search's crops and bands. |

*Decision Margin Cutoff* and *Pose Estimation Iterations* still apply. NAPA computes decision margin the way AprilTag
does, so the same cutoff works.

### OV9281 notes

- The OV9281 is a 1280x800 global-shutter mono sensor. Pick its full 1280x800 MJPEG mode for range. At the default
  16 px minimum NAPA reads every pixel, so its cost does grow with resolution, but it stays several times cheaper than
  AprilTag 3, and decoding the MJPEG frame often costs more CPU than detection. If CPU runs high, try **NAPA Far
  Search** or a lower frame rate before dropping resolution.
- Use a short exposure and raise gain to keep tags sharp while the robot moves. Motion blur is where NAPA's corner
  error is largest.
- With several OV9281s on one Orange Pi, watch CPU in the Settings tab. If it runs high, try **NAPA Far Search**
  before raising the minimum tag size.

### Pixel convention

NAPA reports corners in OpenCV's pixel convention: the centre of the top-left pixel is (0, 0). Camera calibrations
use the same convention. WPILib's AprilTag 3 puts (0, 0) at that pixel's top-left corner, so its corners are 0.5 px
larger in x and y. Switching a camera from AprilTag 3 to NAPA therefore moves its corners by half a pixel and removes
a small bias (about 0.04 degrees of bearing at 1280x800). `NapaDetectorTest` checks that AprilTag 3's corners sit
0.5 px from NAPA's on average.

## What was not ported from Spectrum's fork

Spectrum's fork is built around a Jetson with CUDA and different cameras. These parts don't apply to an Orange Pi
with OV9281s, or depend on Spectrum-only infrastructure, so they were left out:

- **"Tags on CPU (NAPA)"** in the CUDA and AprilTag + Object pipelines. Those pipelines exist only in Spectrum's fork.
- **Streaming NAPA** (photonvision-104), which decodes the MJPEG frame on the CPU (libjpeg-turbo) band by band while
  it arrives and runs NAPA on each band. Spectrum measured about 1 ms less latency. It needs Spectrum's
  `libspectrum-stream.so` and their reworked USB frame provider, so it's a larger port; it would work on an Orange Pi.
- **Camera thread pinning** (photonvision-102) and the **per-frame latency trace** (photonvision-103). Both are
  separate features. Pinning could later help an RK3588 by keeping camera threads on the A76 cores.
- **AprilTag 3 pixel-convention fix** (Spectrum's `AprilTagPixelConvention`). AprilTag 3 output is unchanged here;
  only NAPA's corners use OpenCV's convention.

## Updating NAPA

Replace `third_party/napa/` with the same files from a newer NAPA release. It must be 0.5.0 or later: releases before
0.5.0 used the PolyForm Noncommercial licence. Rerun `build.sh` and check that every patch still applies. SpectrumJetson
keeps its copy and patches in sync with upstream, so compare against theirs first.

## Running the Java tests with the library

`photon-core`'s `NapaDetectorTest` compares NAPA with AprilTag 3 on the test images when it can load `libphoton-napa`
from `java.library.path`, and skips the comparison otherwise. On the coprocessor, or on a Linux PC after `build.sh`:

```bash
LD_LIBRARY_PATH=photon-napa/build ./gradlew :photon-core:test --tests '*NapaDetectorTest'
```
