<template>
  <div class="abc-editor-container">
    <div class="editor-pane">
      <div class="header">
        <h3>ABC Editor</h3>
        <button @click="handleSave">Save</button>
      </div>
      <codemirror
        v-model="abcContent"
        placeholder="Enter ABC code here..."
        :style="{ height: '400px' }"
        :autofocus="true"
        :indent-with-tab="true"
        :tab-size="2"
        :extensions="extensions"
        @change="onEditorChange"
      />
    </div>
    <div class="preview-pane">
      <h3>Preview & Play</h3>
      <div class="controls">
          <label>SoundFont:</label>
          <select v-model="selectedSoundFont" @change="onSoundFontChange">
              <option v-for="sf in soundFonts" :key="sf.url" :value="sf.url">
                  {{ sf.name }}
              </option>
          </select>
          <span v-if="soundFontLoading" class="loading-indicator">Loading SoundFont...</span>
      </div>
      <div class="playback-controls">
          <button @click="play" :disabled="isPlaying || soundFontLoading">Play</button>
          <button @click="pause" :disabled="!isPlaying">Pause</button>
          <button @click="stop" :disabled="!isPlaying && !isPaused">Stop</button>
      </div>
      <div id="abc-paper"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, onBeforeUnmount } from 'vue'
import { Codemirror } from 'vue-codemirror'
import { oneDark } from '@codemirror/theme-one-dark'
import abcjs from 'abcjs'
import 'abcjs/abcjs-audio.css'
import { WorkletSynthesizer, Sequencer } from 'spessasynth_lib'

const props = defineProps({
  initialContent: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['save'])

const abcContent = ref(props.initialContent)
const extensions = [oneDark]

// SoundFonts
const soundFonts = [
    { name: 'Violin (Local)', url: './sf2/Violin_LDK1609.sf2' },
    { name: 'Musyng Kite', url: 'https://paulrosen.github.io/midi-js-soundfonts/MusyngKite/' },
    { name: 'FluidR3_GM', url: 'https://paulrosen.github.io/midi-js-soundfonts/FluidR3_GM/' }
]
const selectedSoundFont = ref(soundFonts[0].url)
const soundFontLoading = ref(false)
const isPlaying = ref(false)
const isPaused = ref(false)

const visualOptions = { responsive: 'resize' }

// Audio Context & Synth
let audioContext = null
let synthesizer = null
let sequencer = null
let visualObj = null
let timingCallbacks = null
let animationId = null

const initAudio = async () => {
    if (!audioContext) {
        audioContext = new (window.AudioContext || window.webkitAudioContext)();
    }

    if (audioContext.state === 'suspended') {
        try {
            await audioContext.resume();
        } catch(e) {
            console.warn("Could not resume audio context", e);
        }
    }

    if (!synthesizer) {
        try {
            await audioContext.audioWorklet.addModule('/spessasynth_processor.min.js');
            synthesizer = new WorkletSynthesizer(audioContext);
            sequencer = new Sequencer(synthesizer);
        } catch (e) {
            console.error("Failed to initialize SpessaSynth", e);
            alert("Audio initialization failed: " + e.message);
            return;
        }
    }

    await loadSoundFont(selectedSoundFont.value);
}

const loadSoundFont = async (url) => {
    if (!synthesizer) return;
    soundFontLoading.value = true;
    try {
        await synthesizer.loadSoundFont(url);
        console.log("SoundFont loaded:", url);
    } catch (e) {
        console.error("Failed to load SoundFont:", url, e);
        if (!url.includes('MusyngKite')) {
             alert(`Failed to load SoundFont: ${url}. Falling back to Musyng Kite...`);
             selectedSoundFont.value = soundFonts[1].url;
             await loadSoundFont(soundFonts[1].url);
        }
    } finally {
        soundFontLoading.value = false;
    }
}

const onSoundFontChange = async () => {
    await loadSoundFont(selectedSoundFont.value);
}

const renderAbc = () => {
  const rendered = abcjs.renderAbc("abc-paper", abcContent.value, visualOptions);
  visualObj = rendered[0];

  if (visualObj) {
      timingCallbacks = new abcjs.TimingCallbacks(visualObj, {
          eventCallback: (ev) => {
              // Can highlight manually if needed
          }
      });
  }
}

const updateCursor = () => {
    if (sequencer && timingCallbacks) {
        const time = sequencer.currentTime;
        // abcjs timing callbacks use milliseconds usually
        timingCallbacks.setProgress(time * 1000); // sequencer uses seconds?
        // SpessaSynth currentTime is in seconds.
        // abcjs setProgress expects milliseconds?
        // Actually, timingCallbacks.setProgress(qpm, time) ? No, setProgress(t) usually.
        // Let's assume ms.
    }

    if (isPlaying.value && !isPaused.value) {
        animationId = requestAnimationFrame(updateCursor);
    }
};

const play = async () => {
    if (!visualObj) return;
    if (!synthesizer) {
        await initAudio();
    }

    if (audioContext.state === 'suspended') {
        await audioContext.resume();
    }

    if (isPaused.value && sequencer) {
        sequencer.play();
        isPaused.value = false;
        isPlaying.value = true;
        updateCursor();
        return;
    }

    // Generate MIDI
    const midiBuffer = abcjs.synth.getMidiFile(visualObj, { type: 'binary' });
    if (!midiBuffer) {
        alert("Could not generate MIDI.");
        return;
    }

    if (midiBuffer.length === 0) {
         alert("Empty MIDI generated.");
         return;
    }

    // SpessaSynth loadNewSongList expects array of { binary: Uint8Array, ... } or just ArrayBuffers
    // `SuppliedMIDIData` can be ArrayBuffer or { name, buffer }
    try {
        // midiBuffer from abcjs is Uint8Array or ArrayBuffer?
        // It returns Uint8Array usually.
        // Sequencer expects SuppliedMIDIData[]
        sequencer.loadNewSongList([midiBuffer.buffer]);
        sequencer.play();
        timingCallbacks.start();
        isPlaying.value = true;
        isPaused.value = false;
        updateCursor();
    } catch (e) {
        console.error("Sequencer error", e);
        alert("Playback failed: " + e.message);
    }
};

const pause = () => {
    if (sequencer) {
        sequencer.pause();
        timingCallbacks.pause();
        isPaused.value = true;
        if (animationId) cancelAnimationFrame(animationId);
    }
};

const stop = () => {
    if (sequencer) {
        sequencer.currentTime = 0;
        sequencer.pause(); // or stop if available
        timingCallbacks.stop();
        isPlaying.value = false;
        isPaused.value = false;
        if (animationId) cancelAnimationFrame(animationId);
    }
};

const onEditorChange = () => {
  renderAbc()
}

onMounted(() => {
  renderAbc()
})

onBeforeUnmount(() => {
    if (sequencer) {
        sequencer.pause(); // stop
    }
    if (audioContext) {
        audioContext.close();
    }
    if (animationId) cancelAnimationFrame(animationId);
})

watch(() => props.initialContent, (newVal) => {
    if (newVal !== abcContent.value) {
        abcContent.value = newVal
        renderAbc()
    }
})

const handleSave = () => {
    emit('save', abcContent.value)
}

</script>

<style scoped>
.abc-editor-container {
  display: flex;
  height: 80vh;
  gap: 20px;
}

.editor-pane {
  width: 40%;
  border-right: 1px solid #ccc;
  padding: 10px;
  display: flex;
  flex-direction: column;
}

.header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10px;
}

.preview-pane {
  width: 60%;
  padding: 10px;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.controls, .playback-controls {
    margin-bottom: 10px;
    display: flex;
    align-items: center;
    gap: 10px;
}

.loading-indicator {
    font-size: 0.8em;
    color: #666;
    font-style: italic;
}

#abc-paper {
  background-color: #f8f8f8; /* Light background for sheet music */
  min-height: 200px;
}
</style>
