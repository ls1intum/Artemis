# Programming exercise trailers

`render_trailer.py` renders two 60-second trailers (1920×1080, 30 fps, with a generated soundtrack) of
the programming exercise module:

- **student**: coding in the online editor, automatic feedback on every push, AI feedback, Iris, working
  in your own IDE, practice mode and exams.
- **instructor**: creating an exercise, drafting and refining the problem statement with Artemis
  Intelligence, collaborative editing, verifying template and solution, grading, static code analysis,
  feedback analysis and manual assessment.

The trailers use the screenshots in `documentation/docs/`, so they change when the screenshots are updated.
The student cut also has three motion graphics drawn by the script (push with test results, scores over
several pushes, and a Git terminal); they illustrate the workflow and are not screenshots of Artemis.

## Requirements

- macOS, because the script uses the system fonts Avenir Next and Menlo
- Python 3.11 or newer
- `ffmpeg` on the `PATH` (`brew install ffmpeg`)

Install the Python dependencies from the repository root:

```bash
python3 -m venv .venv-trailer
.venv-trailer/bin/pip install --require-hashes -r supporting_scripts/trailer/requirements.txt
```

## Render

```bash
# Both cuts into build/trailers/ (takes about two minutes)
.venv-trailer/bin/python supporting_scripts/trailer/render_trailer.py

# One cut into another directory
.venv-trailer/bin/python supporting_scripts/trailer/render_trailer.py --cut student --out ~/Downloads

# Stills at given timestamps (seconds) instead of a video, for checking crops and timing quickly
.venv-trailer/bin/python supporting_scripts/trailer/render_trailer.py --cut instructor --preview 6,12.5,30
```

## Change a trailer

The storyboards are the functions `student()` and `instructor()`. Each scene is a dictionary with:

- `kind`: `shot` (one screenshot with a camera move), `collage` (several screenshots appearing one after
  another), `push`, `attempts`, `terminal` (motion graphics), `kinetic`, `intro`, `langs` or `outro`
- `start` and `end` in seconds; scenes overlap by 0.6 s for the transition
- `tin`: the transition into the scene: `fade`, `slide`, `up`, `zoom` or `wipe`
- `kicker`, `title`, `sub`: the caption
- `cam`: camera keyframes `(time fraction, centre x, centre y, width)`, all relative to the screenshot size,
  with an optional easing function as fifth value
- `spots`: highlighted areas `(start s, end s, (x0, y0, x1, y1))` in screenshot fractions, with an optional
  colour

## Keep the trailers up to date

When a PR changes a screenshot this script uses (search for its file name in `render_trailer.py`), or the
programming exercise UI that a trailer shows, update the trailers in the same PR. Check the affected scenes
with `--preview`, because a new screenshot usually has a different layout: fix the `cam` crops and the `spots`
coordinates so the highlights still sit on the right element, and update captions that no longer describe the
application. Then render both cuts and describe the changes in the PR description. `AGENTS.md` states the
same rule for agents.

The list of programming languages mirrors the `ProgrammingLanguage` enum on the server; update `LANGS` when a
language is added.
