import hashlib
import json
import os
import subprocess

import whisper

VIDEO_PATH = "./videos/video-software-lifecycle.mp4"
LECTURE_ID = 1
LECTURE_UNIT_ID = 1

SLIDE_MAPPING = [
    {
        "start": 0,
        "slideNumber": 1,
    },
    {
        "start": 162,
        "slideNumber": 2,
    },
    {
        "start": 445,
        "slideNumber": 3,
    },
    {
        "start": 690,
        "slideNumber": 4,
    },
    {
        "start": 778,
        "slideNumber": 5,
    },
    {
        "start": 983,
        "slideNumber": 6,
    },
    {
        "start": 1091,
        "slideNumber": 7,
    },
    {
        "start": 1247,
        "slideNumber": 8,
    },
    {
        "start": 1411,
        "slideNumber": 9,
    },
    {
        "start": 1508,
        "slideNumber": 10,
    },
]


def get_video_hash(file_path):
    hasher = hashlib.new("sha256")

    with open(file_path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            hasher.update(chunk)

    return hasher.hexdigest()


def get_raw_transcription(video_path):
    os.makedirs(".tmp/audio", exist_ok=True)
    audio_path = f".tmp/audio/{get_video_hash(video_path)}.mp3"
    subprocess.run(["ffmpeg", "-i", video_path, "-q:a", "0", "-map", "a", audio_path, "-y"])

    model = whisper.load_model("turbo")
    return model.transcribe(audio_path)

def get_slide_number(start_time):
    slide_number = SLIDE_MAPPING[-1]["slideNumber"]
    for mapping in SLIDE_MAPPING:
        if start_time < mapping["start"]:
            break
        slide_number = mapping["slideNumber"]
    return slide_number

def get_transcription(video_path):
    video_hash = get_video_hash(video_path)
    cache_file = f".tmp/transcriptions/{video_hash}.json"
    os.makedirs(".tmp/transcriptions", exist_ok=True)

    if os.path.exists(cache_file):
        with open(cache_file, "r") as file:
            result = json.load(file)
    else:
        result = get_raw_transcription(video_path)
        os.makedirs(".tmp", exist_ok=True)
        with open(cache_file, "w") as file:
            json.dump(result, file, ensure_ascii=False, indent=2)

    output = {
        "lectureId": LECTURE_ID,
        "lectureUnitId": LECTURE_UNIT_ID,
        "language": "en",
        "segments": list(map(lambda item: {
            "startTime": item[1]["start"],
            "endTime": item[1]["end"],
            "text": item[1]["text"],
            "slideNumber": get_slide_number(item[1]["start"])
        }, enumerate(result["segments"])))
    }

    return output


if __name__ == "__main__":
    transcription = get_transcription(VIDEO_PATH)
    print(f"----Transcription----\n\n{json.dumps(transcription, ensure_ascii=False)}")
