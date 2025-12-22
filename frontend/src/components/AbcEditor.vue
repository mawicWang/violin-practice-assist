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
      <div id="abc-paper"></div>
      <div id="abc-audio-controls"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { Codemirror } from 'vue-codemirror'
import { oneDark } from '@codemirror/theme-one-dark'
import abcjs from 'abcjs'
import 'abcjs/abcjs-audio.css'

const props = defineProps({
  initialContent: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['save'])

const abcContent = ref(props.initialContent)
const extensions = [oneDark]

// abcjs visual options
const visualOptions = { responsive: 'resize' }

// abcjs synth (audio) control
let synthControl = null

const renderAbc = () => {
  // 1. Render sheet music
  const visualObj = abcjs.renderAbc("abc-paper", abcContent.value, visualOptions)

  // 2. Setup audio
  if (abcjs.synth.supportsAudio()) {
    if (!synthControl) {
        synthControl = new abcjs.synth.SynthController();
        synthControl.load("#abc-audio-controls", null, {
            displayLoop: true,
            displayRestart: true,
            displayPlay: true,
            displayProgress: true,
            displayWarp: true
        });
    }

    const createSynth = new abcjs.synth.CreateSynth();
    createSynth.init({
        visualObj: visualObj[0],
        options: {
          soundFontUrl: "https://paulrosen.github.io/midi-js-soundfonts/MusyngKite/",
          // soundFontUrl: "https://paulrosen.github.io/midi-js-soundfonts/FluidR3_GM/",
        },
      }).then(() => {
        synthControl.setTune(visualObj[0], false, {}).then(() => {
            console.log("Audio loaded")
        }).catch((error) => {
            console.warn("Audio problem:", error)
        })
    }).catch((error) => {
        console.warn("Audio problem:", error)
    })
  } else {
      document.querySelector("#abc-audio-controls").innerHTML = "<div class='audio-error'>Audio not supported</div>";
  }
}

const onEditorChange = () => {
  renderAbc()
}

onMounted(() => {
  renderAbc()
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

#abc-paper {
  background-color: #f8f8f8; /* Light background for sheet music */
  min-height: 200px;
}

#abc-audio-controls {
    margin-top: 10px;
}
</style>
