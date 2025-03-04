from flask import Flask, request, jsonify, send_file
from flask_cors import CORS
import whisper
import os
import time
import requests
import subprocess
import uuid
import logging

app = Flask(__name__)
CORS(app)  # Enable Cross-Origin Resource Sharing

# Load the Whisper model
model = whisper.load_model("base")

# Setup logging
logging.basicConfig(level=logging.INFO)

TRANSCRIPTS_FOLDER = "transcripts"
os.makedirs(TRANSCRIPTS_FOLDER, exist_ok=True)


def download_video(url, output_path):
    """Download video from a given URL"""
    logging.info(f"Downloading video from {url}...")
    response = requests.get(url, stream=True)
    if response.status_code == 200:
        with open(output_path, 'wb') as f:
            for chunk in response.iter_content(chunk_size=8192):
                f.write(chunk)
        logging.info("Download complete.")
    else:
        raise Exception("Failed to download video")


def extract_audio(video_path, audio_path):
    """Extract audio from video using FFmpeg"""
    logging.info("Extracting audio from video...")
    command = f"ffmpeg -i {video_path} -q:a 0 -map a {audio_path} -y"
    subprocess.run(command, shell=True, check=True)
    logging.info("Audio extraction complete.")


def format_timestamp(seconds):
    """Format time for WebVTT format"""
    return time.strftime('%H:%M:%S', time.gmtime(seconds)) + '.000'


def generate_webvtt(transcription):
    """Convert Whisper transcription to WebVTT format"""
    vtt_output = "WEBVTT\n\n"
    for segment in transcription["segments"]:
        start_time = format_timestamp(segment["start"])
        end_time = format_timestamp(segment["end"])
        text = segment["text"].strip()
        vtt_output += f"{start_time} --> {end_time}\n{text}\n\n"
    return vtt_output


@app.route('/')
def home():
    return "Flask is running!"


@app.route('/transcribe', methods=['POST'])
def transcribe():
    data = request.json
    video_url = data.get("video_url")

    if not video_url:
        return jsonify({"error": "No video URL provided"}), 400

    try:
        unique_id = str(uuid.uuid4())
        video_path = f"/tmp/{unique_id}.mp4"
        audio_path = f"/tmp/{unique_id}.wav"
        vtt_path = os.path.join(TRANSCRIPTS_FOLDER, f"{unique_id}.vtt")


        # Download video and process
        download_video(video_url, video_path)
        extract_audio(video_path, audio_path)
        logging.info("Starting transcription...")
        transcription = model.transcribe(audio_path)

        # Convert to WebVTT
        vtt_content = generate_webvtt(transcription)
        with open(vtt_path, "w") as vtt_file:
            vtt_file.write(vtt_content)

        # Cleanup temporary files
        os.remove(video_path)
        os.remove(audio_path)
        logging.info(f"Transcription completed. Returning VTT: {vtt_path}")

        return send_file(vtt_path, as_attachment=True, download_name="transcription.vtt")

    except Exception as e:
        return jsonify({"error": str(e)}), 500


if __name__ == '__main__':
    app.run(host="0.0.0.0", port=5000)
