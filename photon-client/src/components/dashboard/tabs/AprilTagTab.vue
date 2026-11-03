<script setup lang="ts">
import { AprilTagFamily, PipelineType } from "@/types/PipelineTypes";
import PvSelect from "@/components/common/pv-select.vue";
import PvSlider from "@/components/common/pv-slider.vue";
import PvSwitch from "@/components/common/pv-switch.vue";
import { computed } from "vue";
import { useStateStore } from "@/stores/StateStore";
import type { ActivePipelineSettings } from "@/types/PipelineTypes";
import { useCameraSettingsStore } from "@/stores/settings/CameraSettingsStore";
import { useDisplay, useTheme } from "vuetify";
import { useSettingsStore } from "@/stores/settings/GeneralSettingsStore";

// TODO fix pipeline typing in order to fix this, the store settings call should be able to infer that only valid pipeline type settings are exposed based on pre-checks for the entire config section
// Defer reference to store access method
const currentPipelineSettings = computed<ActivePipelineSettings>(
  () => useCameraSettingsStore().currentPipelineSettings
);
const { mdAndDown } = useDisplay();
const theme = useTheme();
const napaOn = computed(
  () => currentPipelineSettings.value.pipelineType === PipelineType.AprilTag && currentPipelineSettings.value.useNapa
);
// The detector actually running: NAPA when selected and installed, otherwise AprilTag 3. AprilTag 3's decimate,
// blur, threads and refine edges only show while it runs.
const napaActive = computed(() => napaOn.value && useSettingsStore().general.napaWorking);
// NAPA corrects at most 2 bits of tag16h5 (and soft-corrects up to 2 whatever this is set to)
const napaMaxHammingLimit = computed(() =>
  currentPipelineSettings.value.pipelineType === PipelineType.AprilTag &&
  currentPipelineSettings.value.tagFamily === AprilTagFamily.Family16h5
    ? 2
    : 3
);
const interactiveCols = computed(() =>
  mdAndDown.value && (!useStateStore().sidebarFolded || useCameraSettingsStore().isDriverMode) ? 8 : 7
);
</script>

<template>
  <div v-if="currentPipelineSettings.pipelineType === PipelineType.AprilTag">
    <pv-select
      v-model="currentPipelineSettings.tagFamily"
      label="Target family"
      :items="['AprilTag 36h11 (6.5in)', 'AprilTag 16h5 (6in)']"
      :select-cols="interactiveCols"
      @update:modelValue="(value) => useCameraSettingsStore().changeCurrentPipelineSetting({ tagFamily: value }, false)"
    />
    <pv-switch
      v-model="currentPipelineSettings.useNapa"
      :switch-cols="interactiveCols"
      label="Detector: NAPA"
      tooltip="NAPA (by Zachary Wilson, GPL-3.0) instead of AprilTag 3: a CPU AprilTag detector that skips most pixels, finding as many or more tags for a fraction of the CPU. Needs libphoton-napa on the coprocessor (photon-napa/build.sh); without it AprilTag 3 keeps running"
      @update:modelValue="(value) => useCameraSettingsStore().changeCurrentPipelineSetting({ useNapa: value }, false)"
    />
    <v-alert
      v-if="napaOn && !useSettingsStore().general.napaWorking"
      class="mb-2"
      density="compact"
      color="error"
      icon="mdi-alert-circle-outline"
      :variant="theme.global.current.value.dark ? 'tonal' : 'elevated'"
      text="libphoton-napa isn't installed, so AprilTag 3 is still running. Build and install it on the coprocessor with photon-napa/build.sh --install, then restart PhotonVision."
    />
    <template v-if="napaOn">
      <pv-slider
        v-if="!currentPipelineSettings.napaFarSearch"
        v-model="currentPipelineSettings.napaMinTagPx"
        :slider-cols="interactiveCols"
        label="NAPA Min Tag Size (px)"
        tooltip="Smallest tag (black square side, in pixels) NAPA is sure to find. 16 finds far tags; 32 (NAPA's own default) is about 5x faster but misses small, far tags"
        :min="8"
        :max="64"
        @update:modelValue="
          (value) => useCameraSettingsStore().changeCurrentPipelineSetting({ napaMinTagPx: value }, false)
        "
      />
      <pv-slider
        v-model="currentPipelineSettings.napaMaxHamming"
        :slider-cols="interactiveCols"
        label="NAPA Max Error Bits"
        tooltip="Code bits NAPA may correct (replaces AprilTag 3's hamming filter); tags corrected more are dropped. 36h11: keep 1, higher finds a few more tags but risks wrong IDs. 16h5: keep 0, its codes are close together"
        :min="0"
        :max="napaMaxHammingLimit"
        @update:modelValue="
          (value) => useCameraSettingsStore().changeCurrentPipelineSetting({ napaMaxHamming: value }, false)
        "
      />
      <pv-switch
        v-model="currentPipelineSettings.napaFarSearch"
        :switch-cols="interactiveCols"
        label="NAPA Far Search"
        tooltip="Search the whole frame for tags of 32 px and up every frame (replacing Min Tag Size), and for smaller tags (down to the far min size) only around small tags seen in the last half second and in one band of the frame at a time. Cheaper than a small min tag size everywhere"
        @update:modelValue="
          (value) => useCameraSettingsStore().changeCurrentPipelineSetting({ napaFarSearch: value }, false)
        "
      />
      <pv-slider
        v-if="currentPipelineSettings.napaFarSearch"
        v-model="currentPipelineSettings.napaFarMinTagPx"
        :slider-cols="interactiveCols"
        label="NAPA Far Min Tag Size (px)"
        tooltip="Smallest tag searched around recent tags and in the far search's bands"
        :min="8"
        :max="32"
        @update:modelValue="
          (value) => useCameraSettingsStore().changeCurrentPipelineSetting({ napaFarMinTagPx: value }, false)
        "
      />
    </template>
    <pv-slider
      v-if="!napaActive"
      v-model="currentPipelineSettings.decimate"
      :slider-cols="interactiveCols"
      label="Decimate"
      tooltip="Increases FPS at the expense of range by reducing image resolution initially"
      :min="1"
      :max="8"
      @update:modelValue="(value) => useCameraSettingsStore().changeCurrentPipelineSetting({ decimate: value }, false)"
    />
    <pv-slider
      v-if="!napaActive"
      v-model="currentPipelineSettings.blur"
      :slider-cols="interactiveCols"
      label="Blur"
      tooltip="Gaussian blur added to the image, high FPS cost for slightly decreased noise"
      :min="0"
      :max="5"
      :step="0.1"
      @update:modelValue="(value) => useCameraSettingsStore().changeCurrentPipelineSetting({ blur: value }, false)"
    />
    <pv-slider
      v-if="!napaActive"
      v-model="currentPipelineSettings.threads"
      :slider-cols="interactiveCols"
      label="Threads"
      tooltip="Number of threads spawned by the AprilTag detector"
      :min="1"
      :max="8"
      @update:modelValue="(value) => useCameraSettingsStore().changeCurrentPipelineSetting({ threads: value }, false)"
    />
    <pv-slider
      v-model="currentPipelineSettings.decisionMargin"
      :slider-cols="interactiveCols"
      label="Decision Margin Cutoff"
      tooltip="Tags with a 'margin' (decoding quality score) less than this wil be rejected. Increase this to reduce the number of false positive detections"
      :min="0"
      :max="250"
      @update:modelValue="
        (value) => useCameraSettingsStore().changeCurrentPipelineSetting({ decisionMargin: value }, false)
      "
    />
    <pv-slider
      v-model="currentPipelineSettings.numIterations"
      :slider-cols="interactiveCols"
      label="Pose Estimation Iterations"
      tooltip="Number of iterations the pose estimation algorithm will run, 50-100 is a good starting point"
      :min="0"
      :max="500"
      @update:modelValue="
        (value) => useCameraSettingsStore().changeCurrentPipelineSetting({ numIterations: value }, false)
      "
    />
    <pv-switch
      v-if="!napaActive"
      v-model="currentPipelineSettings.refineEdges"
      :switch-cols="interactiveCols"
      label="Refine Edges"
      tooltip="Further refines the AprilTag corner position initial estimate, suggested left on"
      @update:modelValue="
        (value) => useCameraSettingsStore().changeCurrentPipelineSetting({ refineEdges: value }, false)
      "
    />
  </div>
</template>
