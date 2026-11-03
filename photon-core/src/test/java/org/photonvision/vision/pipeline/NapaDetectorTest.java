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

package org.photonvision.vision.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.photonvision.common.LoadJNI;
import org.photonvision.common.configuration.ConfigManager;
import org.photonvision.common.util.TestUtils;
import org.photonvision.jni.NapaDetectorJNI;
import org.photonvision.vision.apriltag.AprilTagFamily;
import org.photonvision.vision.calibration.CameraCalibrationCoefficients;
import org.photonvision.vision.camera.QuirkyCamera;
import org.photonvision.vision.frame.provider.FileFrameProvider;
import org.photonvision.vision.pipeline.result.CVPipelineResult;
import org.photonvision.vision.target.TrackedTarget;

/**
 * NAPA in the AprilTag pipeline. The comparisons need libphoton-napa (photon-napa/build.sh) on
 * java.library.path and are skipped without it; the fallback test runs without it.
 */
public class NapaDetectorTest {
    /** AprilTag 3 reports corners 0.5 px larger than NAPA (OpenCV's pixel convention). */
    private static final double kAprilTagPixelOffset = 0.5;

    @BeforeEach
    public void setup() {
        LoadJNI.loadLibraries();
        ConfigManager.getInstance().load();
    }

    private static CVPipelineResult run(
            boolean napa,
            AprilTagFamily family,
            Path image,
            double fov,
            CameraCalibrationCoefficients calibration) {
        var pipeline = new AprilTagPipeline();
        var s = pipeline.getSettings();
        s.solvePNPEnabled = true;
        s.tagFamily = family;
        s.useNapa = napa;
        // The two detectors measure decision margin a little differently: keep every tag, but accept
        // only uncorrected codes so neither reports spurious ids
        s.decisionMargin = 0;
        s.hammingDist = 0;
        s.napaMaxHamming = 0;
        var frameProvider = new FileFrameProvider(image, fov, calibration);
        frameProvider.requestFrameThresholdType(pipeline.getThresholdType());
        try {
            return pipeline.run(frameProvider.get(), QuirkyCamera.DefaultCamera);
        } finally {
            pipeline.release();
        }
    }

    /**
     * Tags both detectors found must have the same corners (same order, within a pixel) and the same
     * distance (within 5%), and AprilTag 3's corners must sit 0.5 px from NAPA's on average.
     * expectedId: a tag both must find, or -1 to only require one tag in common.
     */
    private static void assertSameTags(
            AprilTagFamily family,
            Path image,
            double fov,
            CameraCalibrationCoefficients calibration,
            int expectedId) {
        assumeTrue(NapaDetectorJNI.isAvailable(), "libphoton-napa not installed");

        var at3 = run(false, family, image, fov, calibration).targets;
        var napa = run(true, family, image, fov, calibration).targets;

        if (expectedId >= 0) {
            assertTrue(
                    at3.stream().anyMatch(t -> t.getFiducialId() == expectedId),
                    "AprilTag 3 found tag " + expectedId);
            assertTrue(
                    napa.stream().anyMatch(t -> t.getFiducialId() == expectedId),
                    "NAPA found tag " + expectedId);
        }

        double sumDx = 0, sumDy = 0;
        int n = 0;
        for (TrackedTarget a : at3) {
            var match = napa.stream().filter(t -> t.getFiducialId() == a.getFiducialId()).findFirst();
            if (match.isEmpty()) continue;
            var ca = a.getTargetCorners();
            var cn = match.get().getTargetCorners();
            assertEquals(4, cn.size());
            for (int c = 0; c < 4; c++) {
                double dx = ca.get(c).x - cn.get(c).x, dy = ca.get(c).y - cn.get(c).y;
                assertEquals(
                        kAprilTagPixelOffset, dx, 1.0, "tag " + a.getFiducialId() + " corner " + c + " x");
                assertEquals(
                        kAprilTagPixelOffset, dy, 1.0, "tag " + a.getFiducialId() + " corner " + c + " y");
                sumDx += dx;
                sumDy += dy;
                n++;
            }
            var ta = a.getBestCameraToTarget3d().getTranslation();
            var tn = match.get().getBestCameraToTarget3d().getTranslation();
            assertEquals(ta.getNorm(), tn.getNorm(), 0.05 * ta.getNorm(), "distance within 5%");
        }
        assertTrue(n > 0, "the detectors found no tag in common");
        assertEquals(kAprilTagPixelOffset, sumDx / n, 0.25, "mean x offset (pixel convention)");
        assertEquals(kAprilTagPixelOffset, sumDy / n, 0.25, "mean y offset (pixel convention)");
    }

    @Test
    public void napaMatchesAprilTag3FacingCamera() {
        assertSameTags(
                AprilTagFamily.kTag36h11,
                TestUtils.getApriltagImagePath(TestUtils.ApriltagTestImages.kTag1_640_480, false),
                TestUtils.WPI2020Image.FOV,
                TestUtils.get2020LifeCamCoeffs(false),
                1);
    }

    @Test
    public void napaMatchesAprilTag3Tag16h5() {
        assertSameTags(
                AprilTagFamily.kTag16h5,
                TestUtils.getApriltagImagePath(TestUtils.ApriltagTestImages.kTag_corner_1280, false),
                TestUtils.WPI2020Image.FOV,
                TestUtils.getCoeffs(TestUtils.LIMELIGHT_480P_CAL_FILE, false),
                -1);
    }

    @Test
    public void fallsBackToAprilTag3WithoutLibrary() {
        assumeFalse(NapaDetectorJNI.isAvailable(), "libphoton-napa is installed");

        var r =
                run(
                        true,
                        AprilTagFamily.kTag36h11,
                        TestUtils.getApriltagImagePath(TestUtils.ApriltagTestImages.kTag1_640_480, false),
                        TestUtils.WPI2020Image.FOV,
                        TestUtils.get2020LifeCamCoeffs(false));
        assertEquals(1, r.targets.size());
        assertEquals(1, r.targets.get(0).getFiducialId());
    }
}
