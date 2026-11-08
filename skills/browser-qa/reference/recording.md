# Recording

Every batch gets a video. A still cannot show that a click caused a change; a video can. Screenshots stay for single states: a rendered screen, a toast, a failure.

## Pick the method once per run

1. **chrome-devtools screencast.** Check that the browser tools include `screencast_start` and `screencast_stop`. If they do, record one clip per batch: start after sign-in at the batch's start screen, stop after its last case. It needs ffmpeg 7.1 or newer and a headed browser.
2. **Recorder harness** (`skills/browser-qa/assets/recorder/recorder.mjs`). Use it when the screencast tools are missing, fail, or leave a 0-byte file. The driver then drives the batch with the harness script instead of the browser tools. Its `events.json` records response status and console events, but bodies require explicit per-case capture; follow `skills/browser-qa/assets/recorder/README.md` and check each required request and response file. If capture fails, mark the affected assertion blocked. It cannot reuse a session signed in by hand.

When neither produces a video, stop and ask the user. Never deliver a GIF or a slideshow of screenshots as a video.

## Taking the clip

- One clip per batch, one role per clip. Name it `video/<batch>.webm`. The screencast tool may save an `.mp4` under the same stem; use the path it reports.
- Real clicks and key-by-key typing only. Pause 2 to 3 seconds after each state change so it stays readable.
- Note the clip time at which each case starts in the case's result (`video_at`), so a reader can jump to it.
- A real app defect on camera is the best evidence there is. Keep that take. A take spoiled by your own misclick gets re-recorded.

## Post-process (orchestrator)

A raw screencast is minutes of near-still frames. Every clip that leaves the run dir is the **trimmed** clip, `<batch>.trimmed.mp4`; the raw recording stays in the run dir as the original.

Screencasts store few frames with long gaps between them. Resample to a constant rate first, then speed up, so each page change stays on screen:

```bash
ffmpeg -y -loglevel error -i <run>/video/<batch>.<ext> \
  -vf "fps=10,setpts=PTS/4,scale=1280:-2" -r 30 -crf 28 -preset fast -an -pix_fmt yuv420p \
  <run>/video/<batch>.trimmed.mp4
ffmpeg -i <run>/video/<batch>.trimmed.mp4 -f null - 2>&1 | grep -o 'time=[0-9:.]*' | tail -1
```

Read the duration from the last `time=`. A trimmed clip runs about a quarter of the raw one. Do not convert `video_at` by dividing by four: screencast frame timing varies. Use the contact sheet below only to find roughly where each case sits, then step through the trimmed clip there and set `video_at` to the moment the case starts. A clip under 20 seconds for a batch of several cases has lost its steps: redo the trim. Dropping frames with `mpdecimate` and renumbering them with `setpts=N/FRAME_RATE/TB` closes every gap and shrinks a ten-minute batch to seconds.

Then build a contact sheet that spans the whole trimmed clip, with N the duration in seconds divided by 24:

```bash
ffmpeg -y -loglevel error -i <run>/video/<batch>.trimmed.mp4 \
  -vf "fps=1/N,scale=320:-1,tile=6x4" -frames:v 1 <run>/video/<batch>-sheet.png
```

The trim is done when you have read the sheet and named, per case of the batch, the moment that shows it, and you have checked a full-size frame of the last case (`-sseof -3 -frames:v 1`). A blank or frozen clip means the recording failed, and the batch is re-driven. Compress a trimmed clip over 10 MB with `-crf 30 -preset veryslow` instead of dropping it.
