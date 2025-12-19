<template>
  <div class="app-container">
    <el-container>
      <el-header>
        <h1>Violin Practice Assistant</h1>
      </el-header>
      <el-main>
        <div v-if="!currentScoreId">
            <Upload @upload-success="refreshList" />
            <el-divider />
            <ScoreList ref="scoreListRef" @select-score="selectScore" />
        </div>
        <div v-else>
            <el-button @click="currentScoreId = null" style="margin-bottom: 20px;">Back to List</el-button>
            <ScoreViewer :scoreId="currentScoreId" />
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import ScoreList from './components/ScoreList.vue';
import ScoreViewer from './components/ScoreViewer.vue';
import Upload from './components/Upload.vue';

const currentScoreId = ref(null);
const scoreListRef = ref(null);

const selectScore = (id) => {
    currentScoreId.value = id;
};

const refreshList = () => {
    if (scoreListRef.value) {
        scoreListRef.value.fetchScores();
    }
};
</script>

<style>
.app-container {
    max-width: 1200px;
    margin: 0 auto;
    padding: 20px;
}
</style>
