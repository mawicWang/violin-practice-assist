<template>
  <div class="upload-container">
    <el-upload
      class="upload-demo"
      drag
      action="/api/scores/upload"
      :http-request="customUpload"
      multiple
    >
      <el-icon class="el-icon--upload"><upload-filled /></el-icon>
      <div class="el-upload__text">
        Drop file here or <em>click to upload</em>
      </div>
      <template #tip>
        <div class="el-upload__tip">
          jpg/png files (OMR) or .xml/.mxl files
        </div>
      </template>
    </el-upload>

    <el-dialog v-model="dialogVisible" title="Upload Score">
        <el-form :model="form">
            <el-form-item label="Title">
                <el-input v-model="form.title" autocomplete="off" />
            </el-form-item>
        </el-form>
        <template #footer>
            <span class="dialog-footer">
                <el-button @click="dialogVisible = false">Cancel</el-button>
                <el-button type="primary" @click="submitUpload">
                Upload
                </el-button>
            </span>
        </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { UploadFilled } from '@element-plus/icons-vue';
import axios from 'axios';

const emit = defineEmits(['upload-success']);

const dialogVisible = ref(false);
const fileToUpload = ref(null);
const form = ref({
    title: ''
});

const customUpload = (options) => {
    fileToUpload.value = options.file;
    form.value.title = options.file.name.split('.')[0];
    dialogVisible.value = true;
};

const submitUpload = async () => {
    if (!fileToUpload.value) return;

    const formData = new FormData();
    formData.append('file', fileToUpload.value);
    formData.append('title', form.value.title);

    try {
        await axios.post('/api/scores/upload', formData, {
            headers: {
                'Content-Type': 'multipart/form-data'
            }
        });
        emit('upload-success');
        dialogVisible.value = false;
        fileToUpload.value = null;
    } catch (error) {
        console.error("Upload failed", error);
        alert("Upload failed");
    }
};
</script>
