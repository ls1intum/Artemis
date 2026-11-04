# Recording

Every batch gets a video. A still cannot show that a click caused a change; a video can. Screenshots stay for single states: a rendered screen, a toast, a failure.

## Pick the method once per run

1. **chrome-devtools screencast.** Check that the browser tools include `screencast_start` and `screencast_stop`. If they do, record one clip per batch: start after sign-in at the batch's start screen, stop after its last case. It needs ffmpeg 7.1 or newer and a headed browser.
2. **Recorder harness** (`skills/browser-qa/assets/recorder/recorder.mjs`). Use it when the screencast tools are missing, fail, or leave a 0-byte file. The driver then drives the batch with the harness script instead of the browser tools. Its `events.json` records response status and console events, but bodies require explicit per-case capture; follow `skills/browser-qa/assets/recorder/README.md` and check each required request and response file. If capture fails, mark the affected assertion blocked. It cannot reuse a session signed in by hand.

When neither produces a video, stop and ask the user. Never deliver a GIF or a slideshow of screenshots as a video.

## Taking the clip

- One clip per batch, one role per clip. Name it `video/<batch>.webm`.
- Real clicks and key-by-key typing only. Pause 2 to 3 seconds after each state change so it stays readable.
- Note the clip time at which each case starts in the case's result (`video_at`), so a reader can jump to it.
- A real app defect on camera is the best evidence there is. Keep that take. A take spoiled by your own misclick gets re-recorded.

## Post-process (orchestrator)

Raw screencasts are mostly still frames. Drop idle frames, re-time, and convert to mp4 in one pass:

```bash
ffmpeg -y -loglevel error -i <run>/video/<batch>.webm \
  -vf "mpdecimate=max=12,setpts=N/FRAME_RATE/TB,scale=1280:-2" -r 30 -crf 28 -preset fast -an \
  <run>/video/<batch>.mp4
ffmpeg -y -loglevel error -i <run>/video/<batch>.mp4 -vf "fps=1,scale=320:-1,tile=6x3" <run>/video/<batch>-sheet.png
```

Look at the contact sheet. Every case of the batch must appear in it; a blank or frozen clip means the recording failed, and the batch is re-driven. Keep the webm as the original. When `mpdecimate` drops the `video_at` marks out of step, re-time them from the sheet. Compress anything over 10 MB with `-crf 30 -preset veryslow` instead of dropping it.
