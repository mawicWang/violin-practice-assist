<template>
  <div class="score-viewer">
    <div v-if="loading">Loading score...</div>
    <div v-else-if="error">Error: {{ error }}</div>
    <AbcEditor v-else :initial-content="abcContent" />
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
</style>
