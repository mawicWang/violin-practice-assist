<template>
  <div class="score-viewer">
    <div class="controls">
      <el-button @click="play" :disabled="!isReady">Play</el-button>
      <el-button @click="pause" :disabled="!isReady">Pause</el-button>
      <el-button @click="stop" :disabled="!isReady">Stop</el-button>
      <span>BPM: </span>
      <el-input-number v-model="bpm" @change="updateBpm" :min="30" :max="300" />
    </div>
    <div ref="scoreContainer" class="score-container"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue';
import { OpenSheetMusicDisplay } from 'opensheetmusicdisplay';
import OsmdAudioPlayer from 'osmd-audio-player';
import axios from 'axios';

const props = defineProps({
  scoreId: {
    type: Number,
    required: true
  }
});

const scoreContainer = ref(null);
const isReady = ref(false);
const bpm = ref(100);

let osmd = null;
let audioPlayer = null;

onMounted(async () => {
  if (scoreContainer.value) {
    osmd = new OpenSheetMusicDisplay(scoreContainer.value, {
      autoResize: true,
      backend: 'svg',
      drawTitle: true,
    });

    audioPlayer = new OsmdAudioPlayer();

    await loadScore();
  }
});

onBeforeUnmount(() => {
    if (audioPlayer) {
        audioPlayer.stop();
    }
});

watch(() => props.scoreId, () => {
  loadScore();
});

const loadScore = async () => {
  if (!props.scoreId) return;

  try {
    isReady.value = false;
    const response = await axios.get(`/api/scores/${props.scoreId}/content`);
    const xmlContent = response.data;

    await osmd.load(xmlContent);
    osmd.render();

    await audioPlayer.loadScore(osmd);
    audioPlayer.setBpm(bpm.value);

    isReady.value = true;
  } catch (error) {
    console.error("Failed to load score:", error);
  }
};

const play = () => {
  if (audioPlayer) {
      if (audioPlayer.state === 'STOPPED' || audioPlayer.state === 'PAUSED') {
          audioPlayer.play();
      }
  }
};

const pause = () => {
  if (audioPlayer) audioPlayer.pause();
};

const stop = () => {
  if (audioPlayer) audioPlayer.stop();
};

const updateBpm = (val) => {
    if (audioPlayer) {
        audioPlayer.setBpm(val);
    }
}

</script>

<style scoped>
.score-viewer {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.score-container {
  width: 100%;
  min-height: 500px;
  border: 1px solid #ccc;
}
.controls {
    display: flex;
    gap: 10px;
    align-items: center;
}
</style>
