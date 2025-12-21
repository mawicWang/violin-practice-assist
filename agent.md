# AGENTS.md

## Project Context
This is the "Violin Practice Assistant" project.
- **Frontend**: Vue 3 + Vite
- **Backend**: Spring Boot 3.x + H2 Database (Gradle)
- **Goal**: Implement features described in README.md.

## Instructions for Agents

1.  **Code Style**:
    -   **Java**: Standard Java conventions. Use Lombok.
    -   **JavaScript/Vue**: Use Composition API `<script setup>`.
2.  **Architecture**:
    -   Keep frontend and backend code strictly separated.
    -   Use the root `package.json` for orchestration.
    -   Use **Gradle** for backend.
3.  **Environment**:
    -   Backend port: 8080
    -   Frontend port: 5173
4.  **Filesystem**:
    -   **OMR**: Using `oemer` and `xml2abc.py`.
    -   Use `storage/` for file uploads.

## Plan Updates
-   **v2.0 Implementation (Current)**:
    -   Replaced manual OMR mock with `oemer` + `xml2abc` pipeline.
    -   Frontend upgraded to use `abcjs` and `codemirror` for text-based music editing.
    -   Backend stores ABC text content instead of just XML paths.
    -   **Update**: Fixed direct upload of XML/MusicXML files by bypassing `oemer` and using `xml2abc` directly. Also added support for direct ABC file uploads.
    -   Status: Core v2.0 structure implemented. OMR pipeline connected. Direct file upload supported.
