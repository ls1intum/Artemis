# Instructor View: Exercise Group Mockup Versions

**Exercise groups** let an instructor bundle several variants of the same task into one unit
with shared properties. Below are the design alternatives we explored, by decision.

---

## Managing groups in the list

The list can be organized as a plain list, by exercise type, by week, or by group. Organized
by group, an instructor can rename a group, drag exercises in and out, reorder them, and set
shared properties (schedule, points cap, hand-in limit, competencies) that override the
individual exercises.

---

## How each exercise is displayed

**Compact**: One dense line per exercise. Fits the most on screen, but nothing aligns, so
comparing an attribute across rows is harder.

**Columnar**: The same details in fixed columns. Fewer per screen, but aligned, so scanning
one column (say, due dates) is easy.

**Table**: A true table with sortable headers. The most structured and familiar for bulk
work, but the heaviest and least Artemis-like.

---

## How actions are presented

Every exercise offers the same actions (scores, edit, create a variant, delete, and so on).
How to surface them:

**Icon only**: Compact icon buttons in the row. Space-saving, but relies on recognizing
icons.

**Text + icon**: Labelled buttons. Clearest, but wide and can crowd out the exercise info.

**Ellipsis menu**: A single "…" opens a menu. Keeps rows clean, but every action is one
extra click and hidden until opened.

---

## Acting on many exercises at once

**Off**: No selection controls; each exercise is managed on its own.

**On**: Checkboxes plus a bulk-action bar for the selection (delete, and for programming
exercises also edit, repository download, consistency check). Best for batch work, but adds
a column and bar everyone sees.

---

## Adding and importing exercises

**None**: No entry point. A clean baseline.

**Inline**: Add and import actions inside each section header, next to the group or type.
Contextual, but repeated for every section.

**Slim**: A compact "Import" and "New" pair in the toolbar, plus a small "+ add" link per
section. Lightweight and unobtrusive, but less prominent.

**Split buttons**: Separate Import, Export, and Create buttons, each opening a focused
dialog. Obvious at a glance, but takes more toolbar room.

**Single button**: One "Manage" button opening a dialog with Create, Import, and Export
tabs. Tidier and scales better, but hides the actions behind a click.

---

## Creating a variant with AI

Two layers: where creation happens, and, for the dialog, how it is laid out.

### Where it happens

**Modal**: Opens a dialog over the list, keeping the instructor in context. Best for a
quick, focused creation.

**Chat**: Opens a full-page workspace with a conversational assistant and an editor. More
room to iterate, but takes the instructor away from the list.

### How the dialog is laid out

_(Applies only to Modal.)_

**Classic**: One form, all options top to bottom. Everything visible and quick for experts,
but dense at first.

**Cards (inline)**: Options as selectable cards in one view. More visual and skimmable,
still on a single screen.

**Cards (wizard)**: The same choices split into guided steps. Most approachable for
first-timers, but more clicks and later steps stay hidden until earlier ones are done.
