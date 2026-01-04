# Web Player for Violin Practice

This is a standalone static web application to play audio files. It is designed to be deployed to GitHub Pages.

## Structure

*   `public/audio/`: Place your MP3 folders here.
    *   `测试`
    *   `GK 音频`
    *   `G1 音频`
    *   `G2 音频`
*   `src/`: Vue 3 source code.

## How to Update Audio Files

1.  Put your `.mp3` files into the corresponding folders in `web-player/public/audio/`.
2.  Run the manifest generator script to update `manifest.json`:
    ```bash
    python3 tools/generate_audio_manifest.py
    ```
    (Note: You must run this from the project root)

## Development

```bash
cd web-player
npm install
npm run dev
```

## Deployment to GitHub Pages

1.  Build the project:
    ```bash
    cd web-player
    npm install
    npm run build
    ```
2.  The output will be in `web-player/dist`.
3.  Deploy the content of `web-player/dist` to your `gh-pages` branch or configure GitHub Pages to serve from the `docs` folder (renaming dist to docs if needed).

### Automated Deployment (Optional)

You can set up a GitHub Action to automatically build and deploy when you push changes.
