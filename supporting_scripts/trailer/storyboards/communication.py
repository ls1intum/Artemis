"""Storyboards of the communication module: channels, messages, Iris tutor suggestions and tutorial groups."""

from pathlib import Path

from PIL import Image
from render_trailer import ACCENT, DOCS, WHITE, ease_io5

STUDENT_ASSETS = DOCS + "/student/assets/communication/"
STUDENT_TUTORIAL = DOCS + "/student/assets/tutorial-groups/"
INSTRUCTOR_ASSETS = DOCS + "/instructor/communication-support/assets/"


def card_for(img, width=1560, max_height=770):
    """Card size with the aspect ratio of the screenshot, so that the whole screenshot fits."""
    iw, ih = Image.open(img).size
    if width * ih / iw <= max_height:
        return (width, round(width * ih / iw))
    return (round(max_height * iw / ih), max_height)


def view(img, box, card):
    """Camera key (cx, cy, wf) that shows the screenshot region box (x0, y0, x1, y1) as large as the card allows."""
    iw, ih = Image.open(img).size
    cw, ch = card
    bw, bh = box[2] - box[0], box[3] - box[1]
    wf = max(bw, bh * ih * cw / (ch * iw))
    return ((box[0] + box[2]) / 2, (box[1] + box[3]) / 2, wf)


def shot(start, end, n, idx, img, kicker, title, sub, tin, spots=(), side=False, card=None, zoom=None, crop=None, **extra):
    """One screenshot with an optional slow zoom into the region zoom (x0, y0, x1, y1).

    crop (x0, y0, x1, y1) shows only that region for the whole scene, with a card of its aspect ratio.
    """
    card = card or (card_for(img, 1060, 760) if side else card_for(img))
    full = (0.5, 0.5, 1.0)
    cam = [(0, *full), (1, *full)]
    if crop:
        iw, ih = Image.open(img).size
        aspect = (crop[2] - crop[0]) * iw / ((crop[3] - crop[1]) * ih)
        card = (round(760 * aspect), 760) if side else (round(770 * aspect), 770)
        key = view(img, crop, card)
        cam = [(0, *key), (1, *key)]
    if zoom:
        target = view(img, zoom, card)
        cam = [(0, *full), (0.3, *full), (0.85, *target, ease_io5), (1, *target)]
    scene = dict(
        kind="shot",
        start=start,
        end=end,
        tin=tin,
        tick=(idx, n),
        img=img,
        kicker=kicker,
        title=title,
        sub=sub,
        cam=cam,
        spots=list(spots),
        card=card,
    )
    if side:
        scene.update(layout="side", card_in="right")
    scene.update(extra)
    return scene


def student():
    n = 10
    a, t = STUDENT_ASSETS, STUDENT_TUTORIAL
    return [
        dict(
            kind="intro",
            start=0,
            end=4,
            kicker="ARTEMIS FOR STUDENTS",
            title="Communication",
            sub="Ask, discuss and learn together.",
        ),
        shot(4, 9, n, 0, a + "message-page.png", "01 · COURSE CHANNELS", "Talk with your whole course.",
             "Course-wide channels for organization, tech support, lectures and exercises.", "zoom",
             spots=[(0.9, 2.5, (0.115, 0.08, 0.285, 0.985)), (2.6, 4.5, (0.285, 0.1, 0.995, 0.99))]),
        shot(9, 14, n, 1, a + "lecture-page.png", "02 · LECTURES", "Ask where you learn.",
             "Start a conversation right on the lecture page.", "slide",
             spots=[(1.0, 4.5, (0.75, 0.12, 0.995, 0.58))]),
        shot(14, 19, n, 2, a + "exercise-page.png", "03 · EXERCISES", "Stuck on an exercise? Ask.",
             "Messages about an exercise live on its detail page.", "wipe",
             spots=[(1.0, 4.5, (0.75, 0.17, 0.995, 0.72))]),
        shot(19, 24, n, 3, a + "direct-message-page.png", "04 · PRIVATE", "Write to your peers.",
             "Direct messages and group chats, with a thread beside the conversation.", "up",
             spots=[(0.9, 2.5, (0.0, 0.14, 0.145, 0.99)), (2.6, 4.5, (0.6, 0.13, 0.995, 0.9))]),
        shot(24, 29, n, 4, a + "formatted-message-toolbar-expanded.png", "05 · COMPOSE", "Format your message.",
             "Bold, code, links, mentions and references, with more options one click away.", "zoom",
             spots=[(0.9, 2.5, (0.0, 0.02, 0.30, 0.27)), (2.6, 4.5, (0.0, 0.28, 0.43, 0.45))],
             card=(1560, 351)),
        shot(29, 34, n, 5, a + "slide-menu-view.png", "06 · REFERENCE", "Point to the exact slide.",
             "Reference exercises, lectures and single lecture slides in your message.", "slide",
             spots=[(1.2, 4.5, (0.39, 0.04, 0.79, 0.87))]),
        shot(34, 39, n, 6, a + "mark-resolved.png", "07 · RESOLVE", "Mark the answer that helped.",
             "Resolved questions show others the right answer and tell moderators what is still open.", "wipe",
             side=True, card=(1060, 616), spots=[(1.0, 4.5, (0.77, 0.36, 0.965, 0.47))]),
        shot(39, 44, n, 7, a + "forward-dialog.png", "08 · FORWARD", "Share a message anywhere in the course.",
             "Forward to several channels or people at once, with your own note.", "up",
             spots=[(0.9, 2.5, (0.02, 0.16, 0.98, 0.28)), (2.6, 4.5, (0.88, 0.85, 0.99, 0.96))]),
        shot(44, 49, n, 8, a + "save-message.png", "09 · SAVE", "Keep it for later.",
             "Saved messages sort into In Progress, Completed and Archived.", "zoom",
             spots=[(0.9, 2.5, (0.14, 0.03, 0.40, 0.21)), (2.6, 4.5, (0.0, 0.70, 0.14, 0.94))]),
        shot(49, 54, n, 9, t + "students-all-groups.png", "10 · TUTORIAL GROUPS", "Find your tutorial group.",
             "See each group's tutor and utilization, then open the details of its sessions.", "wipe",
             spots=[(1.0, 4.5, (0.0, 0.50, 0.66, 0.96))]),
        dict(
            kind="kinetic",
            start=54,
            end=57,
            tin="fade",
            words=[("Ask.", WHITE), ("Discuss.", WHITE), ("Learn.", ACCENT)],
            step=0.4,
            title="Every course conversation in one place.",
            title_t=1.5,
            sub="",
        ),
        dict(
            kind="outro",
            start=57,
            end=60,
            tin="up",
            title="Artemis Communication",
            sub="Learn together, in every course.",
        ),
    ]


def instructor():
    n = 10
    c, i, tg = INSTRUCTOR_ASSETS + "communication/", INSTRUCTOR_ASSETS + "iris/", INSTRUCTOR_ASSETS + "tutorial-groups/"
    return [
        dict(
            kind="intro",
            start=0,
            end=4,
            kicker="ARTEMIS FOR INSTRUCTORS",
            title="Communication",
            sub="Channels, Iris and tutorial groups for your course.",
        ),
        shot(4, 9, n, 0, c + "channel-creation.png", "01 · CHANNELS", "Create channels your way.",
             "Selective or course-wide, public or private, with announcement channels for instructors.", "zoom",
             side=True, spots=[(0.9, 1.9, (0.0, 0.455, 0.21, 0.535)), (2.0, 3.2, (0.0, 0.595, 0.43, 0.67)),
                               (3.3, 4.6, (0.0, 0.76, 0.47, 0.835))]),
        shot(9, 14, n, 1, c + "channel-settings.png", "02 · MODERATE", "Switch privacy in one click.",
             "Moderators change a channel's privacy in its settings.", "slide",
             spots=[(1.0, 4.5, (0.26, 0.44, 0.74, 0.585))]),
        shot(14, 19, n, 2, c + "tutor-suggestions-view.png", "03 · TUTOR SUGGESTIONS", "Iris helps tutors reply.",
             "Context-aware suggestions appear in the thread view when a tutor opens a post.", "wipe",
             side=True, crop=(0.09, 0, 1, 1), spots=[(0.9, 2.5, (0.12, 0.10, 0.64, 0.27)), (2.6, 4.5, (0.12, 0.55, 0.97, 0.80))]),
        shot(19, 24, n, 3, c + "iris-chat-view.png", "04 · ASK IRIS", "Refine with a conversation.",
             "Tutors can chat with Iris right below the suggestions.", "up",
             side=True, crop=(0.11, 0, 1, 1), spots=[(0.9, 2.5, (0.15, 0.16, 0.98, 0.47)), (2.6, 4.5, (0.16, 0.84, 0.97, 0.915))]),
        shot(24, 29, n, 4, c + "tutor-suggestions-history.png", "05 · HISTORY", "Look back at earlier suggestions.",
             "View history shows previously generated suggestions.", "zoom",
             spots=[(1.0, 4.5, (0.895, 0.08, 0.97, 0.5))]),
        dict(
            kind="collage",
            start=29,
            end=34,
            tin="slide",
            tick=(5, n),
            kicker="06 · IRIS SUPPORT LEVEL",
            title="Choose how much Iris helps.",
            sub="One level for the whole course, from Low to High, with an example conversation.",
            items=[
                dict(img=i + "support-level-low.png", scale=0.56, x=70, y=300, t=0.4,
                     spots=[(1.2, 4.5, (0.04, 0.13, 0.96, 0.27))]),
                dict(img=i + "support-level-high.png", scale=0.56, x=985, y=300, t=1.4,
                     spots=[(2.2, 4.5, (0.04, 0.145, 0.96, 0.27))]),
            ],
        ),
        shot(34, 39, n, 6, tg + "instructors-checklist.png", "07 · TUTORIAL GROUPS", "Start with the checklist.",
             "Time zone, default period and managed channels come first.", "wipe",
             spots=[(1.0, 2.6, (0.25, 0.27, 0.88, 0.40)), (2.7, 4.5, (0.25, 0.40, 0.88, 0.51))],
             zoom=(0.10, 0.03, 0.92, 0.75)),
        shot(39, 44, n, 7, tg + "instructors-csv-import.png", "08 · IMPORT", "Create groups from a CSV file.",
             "Import groups and student registrations at once.", "up",
             spots=[(0.9, 2.5, (0.10, 0.12, 0.90, 0.25)), (2.6, 4.5, (0.10, 0.755, 0.90, 0.96))]),
        shot(44, 49, n, 8, tg + "instructors-holidays.png", "09 · FREE DAYS", "Plan around holidays.",
             "A session that falls on a holiday is cancelled automatically.", "zoom",
             spots=[(0.9, 2.5, (0.0, 0.62, 0.65, 0.73)), (2.6, 4.5, (0.66, 0.18, 0.985, 0.32))]),
        shot(49, 54, n, 9, tg + "tutor-sessions.png", "10 · SESSIONS", "Tutors run the sessions.",
             "Cancel or reschedule sessions and enter attendance to see a group's utilization.", "wipe",
             spots=[(0.9, 2.5, (0.0, 0.45, 0.37, 0.70)), (2.6, 4.5, (0.0, 0.71, 1.0, 0.99))]),
        dict(
            kind="kinetic",
            start=54,
            end=57,
            tin="fade",
            words=[("Create.", WHITE), ("Moderate.", WHITE), ("Support.", ACCENT)],
            step=0.4,
            title="Communication, set up for your course.",
            title_t=1.5,
            sub="",
        ),
        dict(
            kind="outro",
            start=57,
            end=60,
            tin="up",
            title="Artemis Communication",
            sub="Channels, Iris and tutorial groups, together.",
        ),
    ]


CUTS = {
    "student": (student, Path(DOCS) / "student" / "assets" / "communication" / "trailer-student.mp4"),
    "instructor": (
        instructor,
        Path(DOCS) / "instructor" / "communication-support" / "assets" / "communication" / "trailer-instructor.mp4",
    ),
}
