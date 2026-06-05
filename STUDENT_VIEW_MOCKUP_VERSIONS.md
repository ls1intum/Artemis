# Student View: Exercise Group Mockup Versions

An **exercise group** bundles several variants of the same task: students can attempt all
of them, but only a limited number count towards their score, so they choose which ones to
hand in. This document compares the design alternatives we explored for showing this in the
student overview, organized by decision. The choices are independent and can be combined.

---

## How the group looks in the sidebar

**Clickable**: The group sits as a slim header above its variants. The variants remain
visible underneath, and opening the group takes the student to a dedicated group page where
they decide what to hand in. A short status line under the header shows how many exercises
have been handed in so far.

**Connected**:  Here the group and its variants are drawn as one connected block, so it
reads as a single unit and stands clearly apart from standalone exercises. It shows the
same hand-in status and likewise opens the group page.

**Select**: There is no separate page at all. Each variant carries a checkbox right in 
the sidebar, so students pick what counts towards their score without leaving the overview.
The trade-off is that there is no room to explain how a  group actually works.

---

## What opens when a group is clicked

_(Applies to the Clickable and Connected directions. Select has no separate page.)_

**Rows page**: The group page presents the selectable variants as full-width rows. It
feels roomy, is easy to read top to bottom, and leaves space for details on each entry.

**Tiles page**: The same selection shown as compact tiles. It reuses already existing
components and feels lighter and fits more on screen at once.

**Exercise page**: The group page adopts the layout of a regular exercise: a header 
with the key facts alongside the group's discussion. Because it reuses the existing
exercise layout, it feels native and blends in with the rest of the app rather than
looking like a separate kind of screen.

---

## How much of each exercise is previewed

On the tile and selection views, each variant can surface a short excerpt from its problem
statement, which helps students tell otherwise-similar variants apart without opening each
one.

**Hide** : No problem statement excerpt, just the title and key facts. The cleanest and 
most compact.

**One, two, or three lines** : Increasing amounts of the problem statement, trading density
for context. A single line only hints at the topic, while three lines are usually enough to
get the gist.

---

## How the tiles are arranged

_(Applies to the tile-based selection.)_

**Stacked** : The tiles sit in a single column, one beneath the next. It is predictable and
easy to read in order.

**Grid** : The tiles flow into a wrapping grid, several to a row, and reflow as the
available width changes. This makes better use of horizontal space when a group has many
variants.
