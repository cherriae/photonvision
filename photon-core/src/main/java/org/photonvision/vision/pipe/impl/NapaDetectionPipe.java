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

package org.photonvision.vision.pipe.impl;

import edu.wpi.first.apriltag.AprilTagDetection;
import java.util.List;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.photonvision.common.logging.LogGroup;
import org.photonvision.common.logging.Logger;
import org.photonvision.jni.NapaDetectorJNI;
import org.photonvision.vision.apriltag.AprilTagFamily;
import org.photonvision.vision.opencv.CVMat;
import org.photonvision.vision.opencv.Releasable;
import org.photonvision.vision.pipe.CVPipe;

/**
 * AprilTag detection with NAPA, the CPU detector by Zachary Wilson ({@link NapaDetectorJNI},
 * GPL-3.0-only), as an alternative to {@link AprilTagDetectionPipe}. Detections are in OpenCV's
 * pixel convention. The native detector is created on the first frame; check {@link #isAvailable()}
 * before using this pipe.
 */
public class NapaDetectionPipe
        extends CVPipe<CVMat, List<AprilTagDetection>, NapaDetectionPipe.NapaDetectionPipeParams>
        implements Releasable {
    private static final Logger logger = new Logger(NapaDetectionPipe.class, LogGroup.VisionModule);

    /**
     * The full-frame pass's smallest tag when far search is on: about 5x cheaper than 16 px, and the
     * crops and sweeps add the small tags back (SpectrumJetson tests/napa-eval).
     */
    private static final int kFarBaseMinTagPx = 32;

    /** Far search sweeps at most this many bands a second. */
    private static final double kFarSweepsPerSecond = 60;

    private static boolean warnedUnavailable = false;

    private long handle = 0;
    private String handleFamily = null;
    private boolean paramsDirty = true;

    /** True when libphoton-napa is installed. Logs once why it isn't. */
    public static synchronized boolean isAvailable() {
        boolean available = NapaDetectorJNI.isAvailable();
        if (!available && !warnedUnavailable) {
            warnedUnavailable = true;
            logger.warn(
                    "NAPA detector selected but lib"
                            + NapaDetectorJNI.LIBRARY_NAME
                            + " isn't installed ("
                            + NapaDetectorJNI.loadError()
                            + "); using AprilTag 3. Build it on the coprocessor with photon-napa/build.sh"
                            + " --install");
        }
        return available;
    }

    @Override
    public void setParams(NapaDetectionPipeParams newParams) {
        if (this.params == null || !this.params.equals(newParams)) paramsDirty = true;
        super.setParams(newParams);
    }

    @Override
    protected List<AprilTagDetection> process(CVMat in) {
        Mat mat = in.getMat();
        if (mat == null || mat.empty() || params == null) return List.of();
        if (mat.type() != CvType.CV_8UC1) {
            logger.error("NAPA needs an 8-bit gray image, got " + CvType.typeToString(mat.type()));
            return List.of();
        }
        if (!ensureDetector()) return List.of();

        // CV_8UC1: the row step in elements is the row step in bytes.
        var ret =
                NapaDetectorJNI.detect(handle, mat.dataAddr(), mat.cols(), mat.rows(), (int) mat.step1());
        return ret == null ? List.of() : List.of(ret);
    }

    /** Creates the native detector for the current family and pushes changed parameters. */
    private boolean ensureDetector() {
        String family = params.family() == null ? "tag36h11" : params.family().getNativeName();
        if (handle == 0 || !family.equals(handleFamily)) {
            release();
            handle = NapaDetectorJNI.create(family);
            handleFamily = family;
            paramsDirty = true;
            if (handle == 0) {
                logger.error("NAPA has no tag family " + family);
                return false;
            }
        }
        if (paramsDirty) {
            NapaDetectorJNI.setParams(
                    handle,
                    params.minTagPx(),
                    params.maxHamming(),
                    params.farSearch(),
                    kFarBaseMinTagPx,
                    params.farMinTagPx(),
                    kFarSweepsPerSecond);
            paramsDirty = false;
        }
        return true;
    }

    @Override
    public void release() {
        if (handle != 0) NapaDetectorJNI.destroy(handle);
        handle = 0;
        handleFamily = null;
    }

    /**
     * @param family tag family
     * @param minTagPx smallest tag (black square side, px) the full-frame pass is sure to find
     * @param maxHamming corrected payload bits, 0-3
     * @param farSearch full frame at 32 px, plus farMinTagPx around tags seen recently and in sweeps
     * @param farMinTagPx smallest tag searched in the far search's crops and sweeps
     */
    public static record NapaDetectionPipeParams(
            AprilTagFamily family, int minTagPx, int maxHamming, boolean farSearch, int farMinTagPx) {}
}
