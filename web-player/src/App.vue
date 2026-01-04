<script setup>
import { ref, computed, onMounted } from 'vue'
import { Search, Folder, VideoPlay, ArrowLeft } from '@element-plus/icons-vue'

const currentPath = ref([]) // Stack of folder names
const audioData = ref({}) // The full manifest
const searchQuery = ref('')
const currentAudio = ref(null) // { name, url, path }

// Fetch manifest
onMounted(async () => {
  try {
    const res = await fetch('./audio/manifest.json')
    audioData.value = await res.json()
  } catch (e) {
    console.error('Failed to load manifest', e)
    // Fallback/Demo data if fetch fails (e.g. locally without server)
    audioData.value = {
      "测试": ["demo1.mp3", "demo2.mp3"],
      "GK 音频": [],
      "G1 音频": [],
      "G2 音频": []
    }
  }
})

// Navigation logic
const navigateTo = (folderName) => {
  currentPath.value.push(folderName)
  searchQuery.value = ''
}

const goBack = () => {
  currentPath.value.pop()
}

// Current folder content
const currentContent = computed(() => {
  if (currentPath.value.length === 0) {
    // Root: list keys of audioData
    return Object.keys(audioData.value).map(key => ({ type: 'folder', name: key }))
  } else {
    // Inside a folder
    const folderName = currentPath.value[0] // Assuming 1 level deep for now as per requirements
    // If we support nested, we'd traverse. Requirements imply simple mapping.
    // Based on user request: root folders -> files
    const files = audioData.value[folderName] || []
    return files.map(f => ({ type: 'file', name: f }))
  }
})

// Search Logic (Global search)
const searchResults = computed(() => {
  if (!searchQuery.value) return []
  const results = []
  for (const [folder, files] of Object.entries(audioData.value)) {
    for (const file of files) {
      if (file.toLowerCase().includes(searchQuery.value.toLowerCase())) {
        results.push({ type: 'file', name: file, folder: folder })
      }
    }
  }
  return results
})

const displayList = computed(() => {
  if (searchQuery.value) return searchResults.value
  return currentContent.value
})

const playAudio = (item) => {
  const folder = item.folder || currentPath.value[0]
  const url = encodeURI(`./audio/${folder}/${item.name}`)
  currentAudio.value = {
    name: item.name,
    folder: folder,
    url: url
  }
}

</script>

<template>
  <div class="container">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <el-button v-if="currentPath.length > 0 && !searchQuery" link :icon="ArrowLeft" @click="goBack">Back</el-button>
          <span>{{ searchQuery ? 'Search Results' : (currentPath.length > 0 ? currentPath[0] : 'Music Library') }}</span>
        </div>
      </template>

      <el-input
        v-model="searchQuery"
        placeholder="Search songs..."
        class="search-box"
        :prefix-icon="Search"
      />

      <el-scrollbar height="400px">
        <el-empty v-if="displayList.length === 0" description="No files found" />

        <div v-else class="list-container">
          <div
            v-for="item in displayList"
            :key="item.name + (item.folder || '')"
            class="list-item"
            @click="item.type === 'folder' ? navigateTo(item.name) : playAudio(item)"
          >
            <el-icon class="item-icon">
              <Folder v-if="item.type === 'folder'" />
              <VideoPlay v-else />
            </el-icon>
            <div class="item-info">
              <div class="item-name">{{ item.name }}</div>
              <div v-if="item.folder" class="item-sub">{{ item.folder }}</div>
            </div>
          </div>
        </div>
      </el-scrollbar>

      <div v-if="currentAudio" class="player-container">
        <div class="now-playing">
          <span class="playing-label">Now Playing:</span> {{ currentAudio.name }}
        </div>
        <audio controls autoplay :src="currentAudio.url" class="audio-control"></audio>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.container {
  max-width: 600px;
  margin: 20px auto;
  padding: 0 10px;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: bold;
}
.search-box {
  margin-bottom: 15px;
}
.list-item {
  display: flex;
  align-items: center;
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background 0.2s;
}
.list-item:hover {
  background-color: #f5f7fa;
}
.item-icon {
  font-size: 20px;
  margin-right: 12px;
  color: #409EFF;
}
.item-info {
  flex: 1;
}
.item-name {
  font-size: 16px;
}
.item-sub {
  font-size: 12px;
  color: #909399;
}
.player-container {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #ebeef5;
}
.now-playing {
  margin-bottom: 10px;
  font-size: 14px;
  color: #303133;
}
.playing-label {
  font-weight: bold;
  color: #409EFF;
}
.audio-control {
  width: 100%;
}
</style>
