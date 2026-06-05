# Instructor View: Exercise Group Mockup Versions

**Exercise groups** let an instructor bundle several variants of the same exercise into one
unit with shared properties. This document compares the design alternatives we explored for
managing them, organized by decision. The choices are independent and can be combined.

---

## Managing groups in the list

The exercise list can be organized in a few ways: a plain list, by exercise type, by week,
or by group. When organized by group, an instructor can rename a group, drag exercises in
and out, reorder them, and set properties that apply to the whole group at once, meaning its
schedule, a points cap, a hand-in limit, and linked competencies. The
alternatives below change how this list looks and how an instructor acts on each exercise
within it.

---

## How each exercise is displayed

**Compact**: Each exercise is a single dense line, with the title and difficulty together
and the dates, points, group, and categories flowing underneath. It fits the most exercises
on screen at once, which suits large courses, but the lack of alignment makes it harder to
compare one attribute across rows.

**Columnar**: The same information is split into fixed columns for the title, dates, points,
categories, and actions. Compared to Compact it shows fewer exercises per screen, but
because everything lines up, scanning a single column (say, all due dates) is far easier.

**Table**: A true table with sortable column headers. It is the most structured of the
three and the most familiar for bulk, spreadsheet-style work; sorting moves into the
headers themselves rather than a separate control. The trade-off is that it feels the least
like the rest of Artemis and is the heaviest visually.

---

## How actions are presented

Every exercise offers the same actions (participations, scores, edit, create a variant,
delete, and so on). The question is how to surface them.

**Icon only**: Each action is a small icon button sitting in the row. It is the most
compact and keeps every action one click away, but it relies on the instructor recognizing
each icon.

**Text + icon**: The same buttons, now labelled. This is the clearest of the three, with no
guessing, but the row of buttons is wide and can crowd out the exercise information,
especially in the denser layouts.

**Ellipsis menu**: A single "…" button opens a small menu of actions. This keeps the rows
clean and uncluttered no matter how many actions exist; the cost is that every action takes
one extra click and is hidden until opened.

---

## Acting on many exercises at once

**Off**: Each exercise is managed on its own. The list stays simple, with no selection
controls.

**On**: A checkbox appears on every exercise and a bulk-action bar shows once anything is
selected, offering operations that apply to the whole selection (delete, and for
programming exercises also edit, repository download, and consistency checks). This is the
better fit when an instructor routinely works on many exercises together; the downside is
the extra column and bar that everyone sees, even when acting on a single exercise.

---

## Adding and importing exercises

**None**: No dedicated entry point. This is the cleanest, used as a baseline to see the list
on its own.

**Inline**: The add and import actions live inside each section header, right next to the
group or type they belong to. This keeps the action close to its context, at the cost of
repeating it for every section.

**Slim**: A compact "Import" and "New" pair in the toolbar, plus a small "+ add" link at the
bottom of each section. It is the lightest-weight option and stays out of the way, but the
actions are smaller and less prominent.

**Split buttons**: Separate Import, Export, and Create buttons in the toolbar, each opening
its own focused dialog. The available actions are obvious at a glance, though they take up
more room in the toolbar.

**Single button**: One "Manage" button that opens a single dialog with Create, Import, and
Export as tabs. Compared to Split buttons this is tidier and scales better as more actions
are added, but it hides the individual actions behind one click.

---

## Creating a variant with AI

This decision has two layers: where variant creation happens, and, if it happens in a
dialog, how that dialog is laid out.

### Where it happens

**Modal**: Creating a variant opens a dialog on top of the exercise list, so the instructor
stays in context and returns to the same place when done. Best for a quick, focused
creation.

**Chat**: Creating a variant opens a dedicated full-page workspace with a conversational
assistant alongside an editor. It gives much more room to iterate on a variant, at the cost
of taking the instructor away from the list.

### How the dialog is laid out

_(Applies only to the Modal option above.)_

**Classic**: A single form with all the options laid out top to bottom. Everything is
visible at once and it is quick for an experienced user, but it can look dense on first
encounter.

**Cards (inline)**: The choices are presented as selectable cards within one view.
Compared to Classic it is more visual and easier to skim, while still keeping everything on
a single screen.

**Cards (wizard)**: The same choices broken into a few guided steps. This is the most
approachable for someone creating a variant for the first time, but it adds clicks and
hides later steps until earlier ones are done.
