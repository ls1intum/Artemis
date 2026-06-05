# Student View: Exercise Group Mockup Versions

An **exercise group** bundles several variants of the same task, of which students hand in
only a limited number for their score. Below are the design alternatives we explored, by
decision.

---

## How the group looks in the sidebar

**Clickable**: A slim header above its variants, which stay visible. Opening it leads to a
group page for choosing what to hand in, with a status line showing how many are handed in.

**Connected**: The group and its variants form one connected block, set clearly apart from
standalone exercises. Same status, and also opens the group page.

---

## What opens when a group is clicked

**Rows page**: Variants as full-width rows. Roomy and easy to scan, with space for details.

**Tiles page**: The same as compact tiles, reusing existing components. Lighter and fits
more on screen.

**Exercise page**: Adopts the regular exercise layout (a header with the key facts
alongside the group's discussion), so it feels native and blends in rather than looking like
a separate screen.

---

## How much of each exercise is previewed

Each tile can show a short excerpt of the problem statement to help tell similar variants
apart.

**Hide**: Title and key facts only. The most compact.

**One, two, or three lines**: More of the statement for more context. One line hints at the
topic; three give the gist.

---

## How the tiles are arranged

_(Applies to the tile-based selection.)_

**Stacked**: A single column, one tile beneath the next. Predictable and easy to read in
order.

**Grid**: A wrapping grid, several tiles per row, reflowing with the width. Better use of
horizontal space when a group has many variants.
