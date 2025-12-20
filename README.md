# Violin Practice Assistant (v2.0) - Oemer + ABC Edition

## 1. 项目概述 (Overview)

本项目是一个本地化运行的小提琴练习辅助工具。
**核心差异点 (v2.0)：** 放弃复杂的图形化修谱，采用 **"图片 -> OMR -> ABC 代码 -> 文本修正 -> 播放"** 的极简工作流。利用 ABC 记谱法的可读性，解决识别不准难以修改的痛点。

## 2. 核心架构 (Architecture)

采用 **Java (Spring Boot)** 管控全局，**Python** 负责计算 (OMR)，**Vue3** 负责交互。

```mermaid
flowchart TD
    subgraph Frontend [前端 (Vue3)]
        UI[用户界面]
        Editor[ABC 文本编辑器 (CodeMirror)]
        Render[实时渲染引擎 (abcjs)]
        Audio[音频合成 (abcjs-audio)]
    end

    subgraph Backend [后端 (Spring Boot)]
        Controller[API 接口]
        DB[(H2 Database)]
        ProcessMgr[进程管理器 (ProcessBuilder)]
    end

    subgraph Python_Environment [Python 环境]
        Oemer[Oemer 识别引擎]
        Converter[xml2abc 转换脚本]
    end

    UI -->|1. 上传图片| Controller
    Controller -->|2. 调用| Oemer
    Oemer -->|3. 生成 MusicXML| Converter
    Converter -->|4. 转为 ABC 文本| Controller
    Controller -->|5. 存入/读取| DB
    DB -->|6. 返回 ABC 字符串| Editor
    Editor -- 7. 实时修改 --> Render
    Render -- 8. 播放 --> Audio

```

---

## 3. 前端编辑器选型 (关键)

针对“ABC 可视化编辑”，单纯用 `<textarea>` 体验不好。我们需要一个带**语法高亮**且能**实时预览**的方案。

### 推荐方案：`abcjs` + `CodeMirror`

这是目前业界最成熟的 Web 端 ABC 编辑组合（类似于 Markdown 编辑器，左边写代码，右边出图）。

* **渲染/播放核心:** **[abcjs](https://github.com/paulrosen/abcjs)**
* 目前功能最强的开源 ABC 库。
* 支持：乐谱 SVG 渲染、MIDI 音频合成、光标跟随、点击音符跳转。


* **编辑器核心:** **[CodeMirror 6](https://codemirror.net/)** (或者 Ace Editor)
* 这是一个代码编辑器组件。
* **优势:** 可以给 ABC 语法（如 `|:`, `T:`, `M:6/8`）加上颜色高亮，让用户看着不晕。


* **现成 Vue 封装:**
* 你可以直接用 `vue-abcjs`，或者手动集成。手动集成灵活度更高。



**前端界面布局设计：**

* **左侧 (40%):** 代码编辑区 (CodeMirror)。显示 `T:标题 M:4/4 K:G ...`
* **右侧 (60%):** 乐谱预览区 (abcjs)。实时显示五线谱。
* **底部:** 播放控制条 (Play/Pause, Speed BPM)。

---

## 4. 后端 OMR 工作流 (Python Integration)

由于 Oemer 是 Python 库，而后端是 Java，我们需要通过“命令行调用”来串联。

### 4.1 环境准备

你需要创建一个 Python 虚拟环境，并安装以下库：

```bash
# 1. 核心识别库
pip install oemer

# 2. 转换库 (把 MusicXML 转 ABC)
# xml2abc 是一个单文件脚本，需下载 xml2abc.py
wget https://wim.vree.org/svgParse/xml2abc.py

```

### 4.2 Java 调用逻辑 (伪代码)

在 Spring Boot 的 Service 层：

```java
public String recognizeImage(String imagePath) {
    // 步骤 1: 调用 Oemer 生成 .musicxml
    // oemer <img_path> -o <output_dir>
    runCommand("oemer", imagePath, "-o", tempDir);

    // 步骤 2: 调用 xml2abc.py 将 .musicxml 转为 .abc 文本
    // python xml2abc.py -o <output.abc> <input.musicxml>
    String xmlPath = tempDir + "/output.musicxml";
    String abcPath = tempDir + "/output.abc";
    runCommand("python", "tools/xml2abc.py", "-o", abcPath, xmlPath);

    // 步骤 3: 读取 .abc 文件内容并返回
    return Files.readString(Paths.get(abcPath));
}

```

---

## 5. 数据库设计 (Schema)

数据结构变得非常简单，我们只需要存**文本**。

**Table: `practice_scores**`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | INT | 主键 |
| `title` | VARCHAR | 曲名 |
| `abc_content` | **TEXT/CLOB** | **核心字段**，存储 ABC 源代码 |
| `original_img` | VARCHAR | 原始图片路径 (留档用) |
| `created_at` | DATETIME | 创建时间 |

---

## 6. 实施路线图 (Roadmap)

### Phase 1: “编辑器与播放器” (Pure Frontend)

**目标：** 先不管识别，做一个好用的 ABC 播放器。

1. 初始化 Vue3 项目。
2. `npm install abcjs codemirror`。
3. 实现：左边改字，右边变谱，点击播放能发声。
4. 找几个现成的 ABC 代码（如刚才的《我和我的祖国》）测试体验。

### Phase 2: “后端与存储” (Spring Boot)

**目标：** 把谱子存下来。

1. 搭建 Spring Boot + H2。
2. 实现 API：`POST /save` (存 ABC), `GET /list` (列表), `GET /detail` (取 ABC)。

### Phase 3: “集成 Oemer” (Python Link)

**目标：** 实现拍照导入。

1. 本地配好 Python 环境，跑通 `oemer` 命令。
2. Java 写 `ProcessBuilder` 串联 Oemer 和 xml2abc。
3. **注意：** Oemer 速度较慢（无 GPU 可能要几十秒），前端需要做一个“正在识别中...”的 Loading 动画。

---

## 7. 给你的开发小贴士

1. **关于 Oemer 的安装：**
Oemer 依赖 TensorFlow 和 OpenCV。在 Mac M1/M2 上安装 TensorFlow 可能会有点麻烦（需要 `tensorflow-macos`）。如果搞不定环境，可以优先考虑用 **Docker** 运行 Oemer，Java 调用 `docker run` 会更稳定。
2. **关于 `xml2abc`：**
Oemer 输出的是 MusicXML。你需要一个工具把它转成 ABC。
推荐使用 **[xml2abc.py](https://wim.vree.org/svgParse/xml2abc.html)**。这是一个非常老牌且稳定的 Python 脚本，完美契合你的 Python 工具链。
3. **编辑器高亮：**
在 CodeMirror 中，你可以简单的把所有以 `L:`, `M:`, `K:` 开头的行设为一种颜色，把 `|` 设为另一种颜色，体验就会提升很多。

现在这个方案比 Audiveris 方案**更轻量、更可控**。如果 Oemer 识别错了，你在网页上把错的字母删掉就行，完全不需要跟图形界面较劲！
