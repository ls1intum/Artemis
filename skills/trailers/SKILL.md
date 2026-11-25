---
name: trailers
description: Render, update or add the 60-second documentation trailers of Artemis modules (programming exercises, exams, lectures and more) that supporting_scripts/trailer/render_trailer.py builds from documentation screenshots.
---

# Documentation trailers

`supporting_scripts/trailer/render_trailer.py` renders silent 60-second videos (1920×1080, 30 fps) from the
screenshots in `documentation/docs/`. Each module has a student and/or an instructor cut that a documentation
page embeds. Read `supporting_scripts/trailer/README.md` first: it has the requirements, the commands and the
scene reference (`kind`, `cam`, `spots`).

## When a trailer needs work

- **A PR changes a screenshot a trailer uses.** Search for the file name in `render_trailer.py` and in
  the module storyboards (`storyboards/<module>.py` next to it). The trailers change in the same PR, not in a follow-up.
- **A PR changes UI that a trailer shows.** Check the affected scenes and captions.
- **A module has none yet.** Add it as described below.

## Update a trailer

1. Set up once: `python3 -m venv .venv-trailer`, then
   `.venv-trailer/bin/pip install --require-hashes -r supporting_scripts/trailer/requirements.txt`.
2. Check the affected scenes with `--module <module> --cut <cut> --preview <seconds,...>` and look at the stills
   in `build/trailers/`. A new screenshot usually has another size or layout, so fix the `cam` crops and the `spots`
   coordinates and rewrite captions that no longer match the application.
3. Every frame where the camera rests, and the last frame of a scene, shows whole rows, headings and controls. A
   crop through a line of text looks like a mistake. Highlights sit on the element the caption names.
4. Render with the default output paths (`--module <module>`), commit the videos, and say in the PR description
   what changed in them.

The committed videos need the macOS fonts (Avenir Next, Menlo). Without them, ask a maintainer with macOS to
render them for the same PR; do not commit videos rendered with other fonts.

## Add a module

1. Create `supporting_scripts/trailer/storyboards/<module>.py` with one storyboard function per cut and `CUTS`
   (see the README, "Add a module"). It is found automatically; no other file registers it.
2. Use only existing documentation screenshots and the scene kinds `kinetic`, `intro`, `shot`, `collage` and
   `outro`. Do not draw UI. The motion graphics `push`, `attempts`, `terminal` and `langs` are programming
   exercise illustrations and must not stand in for screens of another module.
3. Plan about six distinct screenshots per cut. If a cut cannot carry them, leave the cut out and say so in the
   PR instead of padding it.
4. Keep captions short, in English, and limited to what the documentation page states. Use **client** and
   **server**.
5. The scenes add up to exactly 60 seconds; they overlap by 0.6 s.
6. Embed the video in the module's documentation page like the programming exercise pages do (import and
   `<video>` below the introduction, under "Overview" where the page has one). Keep each video near 5 MB.

## Pull request

- One PR per module. Title: `` `Development`: Add trailers for the <module> module ``. Follow `AGENTS.md` for
  the body, milestone and labels.
- A PR that needs the renderer before it is in `develop` stacks on the renderer's branch; switch its base to
  `develop` once that branch is merged.
- Screenshots in the PR description are not needed; the videos are the evidence. State what was and was not
  checked, for example whether the videos were played in a browser.
