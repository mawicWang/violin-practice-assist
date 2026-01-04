import os
import json

AUDIO_DIR = "web-player/public/audio"
OUTPUT_FILE = "web-player/public/audio/manifest.json"

def generate_manifest():
    manifest = {}

    # Ensure directory exists
    if not os.path.exists(AUDIO_DIR):
        print(f"Error: {AUDIO_DIR} does not exist.")
        return

    # Scan directories
    for folder_name in sorted(os.listdir(AUDIO_DIR)):
        folder_path = os.path.join(AUDIO_DIR, folder_name)

        if os.path.isdir(folder_path):
            files = []
            for file in sorted(os.listdir(folder_path)):
                if file.lower().endswith(('.mp3', '.wav', '.ogg', '.m4a')):
                    files.append(file)

            if files: # Only add folders with audio or empty existing known folders
                manifest[folder_name] = files
            elif folder_name in ["测试", "GK 音频", "G1 音频", "G2 音频"]:
                manifest[folder_name] = []

    with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)

    print(f"Manifest generated at {OUTPUT_FILE}")
    print(json.dumps(manifest, ensure_ascii=False, indent=2))

if __name__ == "__main__":
    generate_manifest()
