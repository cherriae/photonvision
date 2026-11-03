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

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.photonvision.vision.apriltag.AprilTagFamily;
import org.photonvision.vision.target.TargetModel;

@JsonTypeName("AprilTagPipelineSettings")
public class AprilTagPipelineSettings extends AdvancedPipelineSettings {
    public AprilTagFamily tagFamily = AprilTagFamily.kTag36h11;
    public int decimate = 1;
    public double blur = 0;
    public int threads = 4; // Multiple threads seems to be better performance on most platforms
    public boolean debug = false;
    public boolean refineEdges = true;
    public int numIterations = 40;
    public int hammingDist = 0;
    public int decisionMargin = 35;
    public boolean doMultiTarget = false;
    public boolean doSingleTargetAlways = false;

    // NAPA, the CPU AprilTag detector by Zachary Wilson (NapaDetectorJNI; GPL-3.0-only), instead of
    // AprilTag 3 (needs libphoton-napa, photon-napa/build.sh). Decimate, blur, threads and refine
    // edges are AprilTag 3's and don't apply; napaMaxHamming replaces hammingDist. Min tag px 16 and
    // max hamming 1 are what Spectrum 3847 measured best on field recordings (NAPA's own default,
    // 32 px, is ~5x faster but misses small tags). Far search: the full frame at 32 px, and
    // napaFarMinTagPx only around tags seen recently and in capped sweeps.
    public boolean useNapa = false;
    public int napaMinTagPx = 16;
    public int napaMaxHamming = 1;
    public boolean napaFarSearch = false;
    public int napaFarMinTagPx = 16;

    // 3d settings

    public AprilTagPipelineSettings() {
        super();
        pipelineType = PipelineType.AprilTag;
        targetModel = TargetModel.kAprilTag6p5in_36h11;
        cameraExposureRaw = 20;
        cameraAutoExposure = false;
        ledMode = false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((tagFamily == null) ? 0 : tagFamily.hashCode());
        result = prime * result + decimate;
        long temp;
        temp = Double.doubleToLongBits(blur);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + threads;
        result = prime * result + (debug ? 1231 : 1237);
        result = prime * result + (refineEdges ? 1231 : 1237);
        result = prime * result + numIterations;
        result = prime * result + hammingDist;
        result = prime * result + decisionMargin;
        result = prime * result + (doMultiTarget ? 1231 : 1237);
        result = prime * result + (doSingleTargetAlways ? 1231 : 1237);
        result = prime * result + (useNapa ? 1231 : 1237);
        result = prime * result + napaMinTagPx;
        result = prime * result + napaMaxHamming;
        result = prime * result + (napaFarSearch ? 1231 : 1237);
        result = prime * result + napaFarMinTagPx;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        AprilTagPipelineSettings other = (AprilTagPipelineSettings) obj;
        if (tagFamily != other.tagFamily) return false;
        if (decimate != other.decimate) return false;
        if (Double.doubleToLongBits(blur) != Double.doubleToLongBits(other.blur)) return false;
        if (threads != other.threads) return false;
        if (debug != other.debug) return false;
        if (refineEdges != other.refineEdges) return false;
        if (numIterations != other.numIterations) return false;
        if (hammingDist != other.hammingDist) return false;
        if (decisionMargin != other.decisionMargin) return false;
        if (doMultiTarget != other.doMultiTarget) return false;
        if (doSingleTargetAlways != other.doSingleTargetAlways) return false;
        if (useNapa != other.useNapa) return false;
        if (napaMinTagPx != other.napaMinTagPx) return false;
        if (napaMaxHamming != other.napaMaxHamming) return false;
        if (napaFarSearch != other.napaFarSearch) return false;
        if (napaFarMinTagPx != other.napaFarMinTagPx) return false;
        return true;
    }
}
