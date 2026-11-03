/*
 * Copyright (C) Photon Vision.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.photonvision.jni;

import edu.wpi.first.apriltag.AprilTagDetection;

/**
 * NAPA, a sparse-sampling CPU AprilTag detector by Zachary Wilson (github.com/zwatx/NAPA, v0.5.1,
 * GPL-3.0-only; ExternalLicenses/NAPA-License), as libphoton-napa.so, built on the coprocessor by
 * photon-napa/build.sh. Ported from Spectrum 3847's PhotonVision fork and SpectrumJetson.
 *
 * <p>One handle per pipeline; a handle is not shared between threads. Detections come back in
 * AprilTag's corner order and OpenCV's pixel convention (pixel centres at integer coordinates;
 * WPILib's AprilTag detector reports them 0.5 px larger). The library is optional: {@link
 * #isAvailable()} is false when it isn't installed, and callers keep their normal detector.
 */
public final class NapaDetectorJNI {
    public static final String LIBRARY_NAME = "photon-napa";

    private static final boolean available;
    private static final String loadError;

    static {
        boolean ok = false;
        String err = null;
        try {
            System.loadLibrary(LIBRARY_NAME);
            ok = true;
        } catch (UnsatisfiedLinkError | SecurityException e) {
            err = e.getMessage();
        }
        available = ok;
        loadError = err;
    }

    private NapaDetectorJNI() {}

    /** True when libphoton-napa is loaded. The first call tries to load it. */
    public static boolean isAvailable() {
        return available;
    }

    /** Why the library didn't load, or null. */
    public static String loadError() {
        return loadError;
    }

    /** A detector for a NAPA family name ("tag36h11", "tag16h5"); 0 if unknown. */
    public static native long create(String family);

    public static native void destroy(long handle);

    /**
     * Far search off: one full-frame pass that finds tags down to minTagPx (black square side, px).
     * Far search on: the full frame at baseMinTagPx every frame, plus farMinTagPx in tracking crops
     * around small tags and in capped sweeps of one band at a time (at most sweepsPerSecond bands a
     * second). maxHamming: corrected payload bits (0-3).
     */
    public static native void setParams(
            long handle,
            int minTagPx,
            int maxHamming,
            boolean far,
            int baseMinTagPx,
            int farMinTagPx,
            double sweepsPerSecond);

    /** Detects tags in an 8-bit gray image: data address, width, height and row step in bytes. */
    public static native AprilTagDetection[] detect(
            long handle, long dataAddr, int width, int height, int step);

    /** frames, base ms, crops, crop ms, sweeps, sweep ms, far tags (totals since create). */
    public static native double[] stats(long handle);
}
