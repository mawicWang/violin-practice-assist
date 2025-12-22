<template>
  <div class="score-viewer">
    <div v-if="loading">Loading score...</div>
    <div v-else-if="error">Error: {{ error }}</div>
    <div v-else>
      <div v-if="xmlContent" class="xml-download">
         <button @click="downloadXml">Download MusicXML</button>
      </div>
      <AbcEditor :initial-content="abcContent" @save="onSave" />
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue';
import axios from 'axios';
import AbcEditor from './AbcEditor.vue';

const props = defineProps({
  scoreId: {
    type: Number,
    required: true
  }
});

const abcContent = ref('');
const xmlContent = ref('');
const loading = ref(true);
const error = ref(null);

const loadScore = async () => {
    if (!props.scoreId) return;

    try {
        loading.value = true;
        error.value = null;
        const response = await axios.get(`/api/scores/${props.scoreId}`);
        // Ensure we handle both cases where abcContent might be direct or in the object
        if (response.data && response.data.abcContent) {
            abcContent.value = response.data.abcContent;
            xmlContent.value = response.data.xmlContent || '';
        } else {
             // Fallback or error if no content
             abcContent.value = "T: No Content\nK: C\n";
        }
    } catch (err) {
        console.error("Failed to fetch score", err);
        error.value = "Failed to load score content.";
    } finally {
        loading.value = false;
    }
};

const onSave = async (newContent) => {
    try {
        await axios.post('/api/scores/save', {
            id: props.scoreId,
            abcContent: newContent
        });
        // Reload to get potential updates (like xml content)
        await loadScore();
        alert('Saved successfully!');
    } catch (err) {
        console.error("Failed to save score", err);
        alert('Failed to save score.');
    }
};

const downloadXml = () => {
    if (!xmlContent.value) return;
    const blob = new Blob([xmlContent.value], { type: 'application/vnd.recordare.musicxml+xml' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `score_${props.scoreId}.musicxml`;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    document.body.removeChild(a);
};

onMounted(() => {
    loadScore();
});

watch(() => props.scoreId, () => {
    loadScore();
});
</script>

<style scoped>
.score-viewer {
    width: 100%;
}
.xml-download {
    margin-bottom: 10px;
    text-align: right;
}
</style>
