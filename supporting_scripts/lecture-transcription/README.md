# Video Transcription and Slide Mapping Script

This script extracts audio from a video file, transcribes it using OpenAI's Whisper model, and maps each transcribed segment to the corresponding slide numbers based on predefined timestamps. The final output is structured in a format suitable for ingestion into Artemis

## Requirements

- Python 3.14 (tested with this version)
- `ffmpeg` (must be installed and accessible via command line)
- The dependencies listed in `requirements.txt`

## Usage

1. Update the `VIDEO_PATH` variable in `script.py` to point to your desired video file.
2. Set the `LECTURE_ID` and `LECTURE_UNIT_ID` variables in `main.py` to match your lecture and unit identifiers.
3. Run the script: `python main.py`
4. The transcription will be printed to the console.

## Configuration

Modify the following variables in `script.py`:

- `VIDEO_PATH`: Path to the video file.
- `LECTURE_ID` and `LECTURE_UNIT_ID`: Identifiers for the lecture and unit.
- `SLIDE_MAPPING`: A list mapping timestamps to slide numbers.
