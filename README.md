
# Violin Practice Assistant - 项目技术设计文档

## 1\. 项目概述 (Overview)

### 1.1 背景

本项目旨在开发一个辅助小提琴练习的本地化工具。针对纸质琴谱练习时缺乏伴奏、速度难以控制的痛点，提供“拍照识别 -\> 数字化琴谱 -\> 变速跟练”的一站式解决方案。

### 1.2 核心功能

1.  **琴谱识别 (OMR):** 用户上传琴谱图片，系统识别并转换为 MusicXML 格式。
2.  **智能琴谱管理:** 本地存储乐谱文件与元数据。
3.  **交互式播放:**
      * Web 端直接渲染五线谱。
      * 支持**变速播放** (BPM 调整)。
      * 播放时光标随音符移动 (Visual Cursor)。
      * 支持加载小提琴音色 (SoundFont)。

-----

## 2\. 系统架构 (System Architecture)

本项目采用 **前后端分离 (B/S)** 架构，并在本地环境中运行。

```mermaid
graph TD
    User["用户 (浏览器):] -->|HTTP 请求| Frontend["前端 (Vue3 + OSMD)"]
    Frontend -->|API 调用| Backend["后端 (Spring Boot)"]
    
    subgraph "Local Environment (本地机器)"
        Frontend -- 渲染/播放 --> AudioEngine[Web Audio API / Tone.js]
        Backend -->|读写| DB[(H2 Database / File Mode)]
        Backend -->|存储/读取| FileSys[本地文件系统 (Images/XML)]
        Backend -->|CLI 调用| OMR[Audiveris Engine (Java Process)]
    end
```

-----

## 3\. 技术选型 (Technology Stack)

### 3.1 前端 (Frontend)

  * **框架:** **Vue 3** + **Vite** (轻量、热更新快)。
  * **乐谱渲染:** **OpenSheetMusicDisplay (OSMD)** (基于 VexFlow，MusicXML 渲染的标准库)。
  * **音频播放:** **osmd-audio-player** (配合 OSMD 实现光标同步与音频合成)。
  * **UI 组件库:** Element Plus 或 Naive UI (可选，用于快速搭建界面)。
  * **通信:** Axios。

### 3.2 后端 (Backend)

  * **框架:** **Spring Boot 3.x**。
  * **数据库:** **H2 Database** (文件模式)。
      * *理由:* 无需安装额外数据库软件，数据即文件，适合单机应用。
  * **ORM:** Spring Data JPA。
  * **OMR 引擎:** **Audiveris** (外部调用)。
      * 通过 `ProcessBuilder` 调用本地 jar 包或 Docker 容器。

### 3.3 开发与运维 (DevOps)

  * **构建工具:** Maven (后端), npm (前端)。
  * **运行管理:** **concurrently** (npm 包)。
      * *作用:* 一个命令同时启动前后端，统一日志输出。

-----

## 4\. 项目结构 (Directory Structure)

```text
ViolinPracticeTool/
├── package.json              # 根项目配置 (用于统一启动)
├── README.md                 # 说明文档
├── tools/                    # 外部工具存放
│   └── Audiveris/            # Audiveris 程序目录
├── backend/                  # Spring Boot 项目
│   ├── src/main/java/com/violin/
│   │   ├── controller/       # API 接口
│   │   ├── entity/           # 数据库实体 (Score)
│   │   ├── service/          # 业务逻辑 (OmrService)
│   │   └── ViolinApplication.java
│   ├── src/main/resources/
│   │   └── application.properties # H2 配置, 存储路径配置
│   └── pom.xml
└── frontend/                 # Vue3 项目
    ├── src/
    │   ├── components/       # ScoreViewer.vue, Upload.vue
    │   └── assets/           # 静态资源 (soundfonts)
    ├── vite.config.js        # 包含反向代理配置
    └── package.json
```

-----

## 5\. 数据库设计 (Database Design)

仅需一张核心表 `scores` 存储乐谱信息。

**表名: `scores`**

| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | Long (PK) | 主键，自增 |
| `title` | Varchar | 曲名 (用户自定义或自动生成) |
| `original_image_path` | Varchar | 原始图片存储路径 (相对路径) |
| `music_xml_path` | Varchar | 识别后的 XML 文件路径 |
| `created_at` | Timestamp | 创建时间 |
| `last_played_at` | Timestamp | 最后练习时间 |
| `tags` | Varchar | 标签 (如: "练习曲", "开塞", "音阶") |

-----

## 6\. 核心业务流程 (Core Workflows)

### 6.1 上传与识别流程

1.  用户在前端点击“上传图片”。
2.  前端将图片 `POST` 到 `/api/scores/upload`。
3.  **后端处理:**
      * 保存图片到本地 `storage/images/`。
      * 创建数据库记录 (状态: `PROCESSING`)。
      * **异步调用** Audiveris 命令行: `java -jar Audiveris.jar -batch -export ...`。
      * 监听 Audiveris 输出，完成后获取生成的 `.mxl` 文件路径。
      * 更新数据库记录 (状态: `READY`, 填入 `music_xml_path`)。
4.  前端轮询或接收通知，显示“识别完成”。

### 6.2 练习与播放流程

1.  前端请求乐谱详情 `/api/scores/{id}`。
2.  后端读取本地 `.mxl` 文件内容，直接返回文本流。
3.  **前端处理:**
      * OSMD 加载 XML 字符串并渲染五线谱。
      * 加载 `Violin.sf2` (SoundFont 音色库)。
      * 用户设置 BPM (如 60)。
      * 点击播放 -\> `osmd-audio-player` 合成音频 -\> 光标随动。

-----

## 7\. 实施步骤与路线图 (Roadmap)

### Phase 1: 基础播放器 (MVP)

  * **目标:** 验证前端渲染和播放能力（不涉及后端识别）。
  * **任务:**
    1.  搭建 Vue3 脚手架。
    2.  引入 OSMD，能够加载本地静态的 `.mxl` 文件。
    3.  引入 `osmd-audio-player`，加载 SoundFont，实现变速播放。

### Phase 2: 后端与存储

  * **目标:** 实现文件上传和持久化。
  * **任务:**
    1.  搭建 Spring Boot，配置 H2 数据库。
    2.  实现“文件上传接口”和“列表查询接口”。
    3.  实现前后端联调 (配置 Vite Proxy)。

### Phase 3: OMR 识别集成

  * **目标:** 打通图片转乐谱的链路。
  * **任务:**
    1.  本地配置 Audiveris 环境。
    2.  编写 Java `ProcessBuilder` 代码调用 Audiveris。
    3.  处理识别失败的异常情况。

-----

## 8\. 启动与开发指南 (Development Guide)

### 8.1 环境要求

  * Java JDK 17+
  * Node.js 16+
  * Audiveris (已安装或下载 jar 包)

### 8.2 快速启动

本项目配置了统一启动脚本。

1.  **初次安装依赖:**

    ```bash
    npm run install:all
    ```

    *(该命令会自动安装 frontend 的 node\_modules 和 backend 的 maven 依赖)*

2.  **启动开发环境:**

    ```bash
    npm run dev
    ```

      * **Backend:** 运行在 `http://localhost:8080`
      * **Frontend:** 运行在 `http://localhost:5173` (自动代理 API 请求到 8080)

3.  **使用:**
    打开浏览器访问 `http://localhost:5173` 即可开始使用。

-----

## 9\. 风险与对策

  * **识别率问题:** OMR 对手写谱或模糊照片支持不佳。
      * *对策:* 前端增加提示，建议使用扫描类 App 拍照；MVP 阶段允许用户直接上传现成的 MusicXML 文件作为补充。
  * **音色问题:** 浏览器合成小提琴声音可能缺乏质感。
      * *对策:* 使用高质量的 SoundFont (如 Sonatina Symphonic Orchestra)。
