<template>
  <div class="score-list">
    <h3>My Scores</h3>
    <el-table :data="scores" style="width: 100%" @row-click="handleRowClick">
      <el-table-column prop="id" label="ID" width="50" />
      <el-table-column prop="title" label="Title" />
      <el-table-column prop="status" label="Status" />
      <el-table-column prop="createdAt" label="Created At" />
      <el-table-column label="Actions">
        <template #default="scope">
          <el-button size="small" @click="handlePlay(scope.row)" :disabled="scope.row.status !== 'READY'">
            Practice
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import axios from 'axios';

const emit = defineEmits(['select-score']);
const scores = ref([]);

const fetchScores = async () => {
  try {
    const response = await axios.get('/api/scores');
    scores.value = response.data;
  } catch (error) {
    console.error("Failed to fetch scores", error);
  }
};

const handleRowClick = (row) => {
    // optional
};

const handlePlay = (score) => {
    emit('select-score', score.id);
};

onMounted(() => {
  fetchScores();
});

defineExpose({ fetchScores });
</script>
