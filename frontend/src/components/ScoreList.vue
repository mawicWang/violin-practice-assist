<template>
  <div class="score-list-container">
    <h3>My Scores</h3>
    <el-table :data="scores" style="width: 100%" @row-click="handleRowClick">
      <el-table-column prop="id" label="ID" width="50" />
      <el-table-column prop="title" label="Title" />
      <el-table-column prop="createdAt" label="Created At" />
      <el-table-column label="Actions">
        <template #default="scope">
          <el-button size="small" @click="handlePlay(scope.row)">
            Practice
          </el-button>
          <el-button size="small" type="danger" @click="handleDelete(scope.row)">
            Delete
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

const handleDelete = async (score) => {
  if (confirm('Are you sure you want to delete this score?')) {
    try {
      await axios.delete(`/api/scores/${score.id}`);
      fetchScores();
    } catch (error) {
      console.error("Failed to delete score", error);
    }
  }
};

onMounted(() => {
  fetchScores();
});

defineExpose({ fetchScores });
</script>
