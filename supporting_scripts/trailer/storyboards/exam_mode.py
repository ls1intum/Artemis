"""Storyboards of the exam mode trailers: the student cut and the instructor cut.

Both cuts use only screenshots of the documentation. Every shot is 4.7 s long, so that the intro (4 s), eleven
shots and the outro add up to 60 s with the 0.6 s transition overlap.
"""

from pathlib import Path

from render_trailer import ACCENT, DOCS, WHITE, ease_io5

SS = DOCS + "/student/assets/exams/"
IS = DOCS + "/instructor/exams/assets/"

SHOT = 4.7
FIRST_END = 4.0
TRANSITIONS = ["zoom", "slide", "wipe", "up"]


def sequence(first, shots, last):
    """Place the intro, the shots and the outro one after another and number the shots."""
    n = len(shots)
    scenes = [dict(first, start=0, end=FIRST_END)]
    for i, shot in enumerate(shots):
        start = FIRST_END + i * SHOT
        base = {"kind": "shot", "tin": TRANSITIONS[i % len(TRANSITIONS)], "tick": (i, n)}
        scenes.append({**base, **shot, "start": round(start, 2), "end": round(start + SHOT, 2)})
    end = scenes[-1]["end"]
    scenes.append(dict(last, kind="outro", start=end, end=60, tin="up"))
    return scenes


def student():
    return sequence(
        dict(
            kind="kinetic",
            words=[("Read.", WHITE), ("Write.", WHITE), ("Submit.", ACCENT)],
            step=0.6,
            title="Exams in Artemis",
            title_t=2.2,
            sub="Take your exam in the browser.",
            dim=False,
        ),
        [
            dict(
                img=SS + "access_exam.png",
                card=(1560, 486),
                kicker="FIND YOUR EXAM",
                title="Your exam waits in the course.",
                sub="The course overview lists the current exam with its start and its points.",
                cam=[(0, 0.5, 0.5, 1.04), (1, 0.5, 0.5, 1.0)],
                spots=[(0.9, 4.2, (0.0, 0.1, 0.998, 0.31))],
            ),
            dict(
                img=SS + "welcome_page.png",
                card=(1560, 675),
                kicker="BEFORE YOU START",
                title="Read the rules, sign, start.",
                sub="Confirm the conditions and enter your full name to begin.",
                cam=[(0, 0.5, 0.5, 1.0), (0.3, 0.5, 0.5, 1.0), (0.6, 0.58, 0.4, 0.84, ease_io5), (1, 0.58, 0.4, 0.84)],
                spots=[(2.3, 3.2, (0.16, 0.28, 0.95, 0.4)), (3.3, 4.5, (0.16, 0.41, 0.42, 0.5))],
            ),
            dict(
                img=SS + "exam_overview.png",
                card=(1560, 521),
                kicker="STAY ON TRACK",
                title="Every exercise at a glance.",
                sub="The overview shows the status and the points of each one.",
                cam=[(0, 0.5, 0.5, 1.04), (1, 0.5, 0.5, 1.0)],
                spots=[(0.8, 2.5, (0.22, 0.24, 0.93, 0.68)), (2.6, 4.4, (0.75, 0.0, 0.99, 0.1))],
            ),
            dict(
                img=SS + "exam_programming_exercises.png",
                card=(1560, 735),
                kicker="PROGRAMMING",
                title="Code in the online editor.",
                sub="Edit files, follow the tasks and submit your solution.",
                cam=[(0, 0.5, 0.38, 1.0), (0.45, 0.5, 0.38, 1.0), (0.85, 0.615, 0.335, 0.77, ease_io5), (1, 0.615, 0.335, 0.77)],
                spots=[(0.8, 2.0, (0.925, 0.08, 0.999, 0.132))],
            ),
            dict(
                img=SS + "exam_quiz_exercises.png",
                card=(1560, 538),
                kicker="QUIZ",
                title="Tick the right answers.",
                sub="Answers are saved automatically every 30 seconds.",
                cam=[(0, 0.5, 0.2575, 1.02), (1, 0.5, 0.2575, 1.0)],
                spots=[(0.8, 2.8, (0.03, 0.205, 0.985, 0.485)), (2.9, 4.4, (0.885, 0.0, 0.999, 0.045))],
            ),
            dict(
                img=SS + "exam_text_exercises.png",
                card=(1560, 647),
                kicker="TEXT",
                title="Write your answer.",
                sub="Type in the editor with the problem statement beside it.",
                cam=[(0, 0.5, 0.5, 1.03), (1, 0.5, 0.5, 1.0)],
                spots=[(0.8, 2.4, (0.645, 0.115, 0.998, 0.47)), (2.5, 4.4, (0.015, 0.265, 0.245, 0.335))],
            ),
            dict(
                img=SS + "exam_modeling_exercises.png",
                card=(1309, 770),
                kicker="MODELING",
                title="Draw your model.",
                sub="Build UML diagrams in the built-in editor.",
                cam=[(0, 0.5, 0.5, 1.03), (1, 0.5, 0.5, 1.0)],
                spots=[(0.8, 2.6, (0.0, 0.095, 0.63, 0.995)), (2.7, 4.4, (0.645, 0.095, 0.998, 0.4))],
            ),
            dict(
                img=SS + "exam_end_screen.png",
                layout="side",
                card=(1100, 765),
                card_in="right",
                kicker="HAND IN",
                title="Finish with confidence.",
                sub="Confirm, sign with your full name and finish — or go back to keep working.",
                cam=[(0, 0.5, 0.5, 1.0)],
                spots=[
                    (0.8, 2.3, (0.03, 0.255, 0.96, 0.43)),
                    (2.4, 3.5, (0.03, 0.48, 0.96, 0.64)),
                    (3.6, 4.5, (0.72, 0.84, 0.96, 0.92)),
                ],
            ),
            dict(
                img=SS + "exam_summary.png",
                card=(1560, 716),
                kicker="YOUR SUMMARY",
                title="Review what you submitted.",
                sub="Your points per exercise and your grade appear here once the results are published.",
                cam=[(0, 0.5, 0.509, 1.0)],
                spots=[(0.8, 2.6, (0.01, 0.34, 0.99, 0.66)), (2.7, 4.4, (0.02, 0.62, 0.4, 0.665))],
            ),
            dict(
                img=SS + "complaint.png",
                card=(1560, 770),
                kicker="COMPLAINTS",
                title="Disagree? File a complaint.",
                sub="Explain your concern within the review period and read the response.",
                cam=[(0, 0.5, 0.5, 1.03), (1, 0.5, 0.5, 1.0)],
                spots=[(0.8, 2.4, (0.02, 0.745, 0.5, 0.97)), (2.5, 4.4, (0.505, 0.745, 0.985, 0.97))],
            ),
            dict(
                img=SS + "exam_example_solutions.png",
                card=(1560, 447),
                kicker="EXAMPLE SOLUTIONS",
                title="Learn from the solutions.",
                sub="Compare your answers with an example solution once it is available.",
                cam=[(0, 0.5, 0.5, 1.04), (1, 0.5, 0.5, 1.0)],
                spots=[(0.8, 2.4, (0.86, 0.24, 0.99, 0.35)), (2.5, 4.4, (0.77, 0.75, 0.99, 0.84))],
            ),
        ],
        dict(title="Artemis Exams", sub="Work in the browser and come back for your results."),
    )


def instructor():
    return sequence(
        dict(
            kind="intro",
            kicker="ARTEMIS FOR INSTRUCTORS",
            title="Online Exams",
            sub="From the first draft to the published grades.",
        ),
        [
            dict(
                img=IS + "exam_checklist_overview.png",
                card=(1560, 451),
                kicker="01 · PLAN",
                title="Follow the exam checklist.",
                sub="Every stage shows what is done and what is still open.",
                cam=[(0, 0.5, 0.5, 1.04), (1, 0.5, 0.5, 1.0)],
                spots=[(0.8, 2.5, (0.45, 0.4, 0.66, 0.58)), (2.6, 4.4, (0.745, 0.4, 0.995, 0.69))],
            ),
            dict(
                kind="collage",
                kicker="02 · SCHEDULE",
                title="Set every date in one form.",
                sub="Working time, grace period, correction rounds and the review window.",
                items=[
                    dict(
                        img=IS + "exam_conduction.png",
                        scale=0.55,
                        x=262,
                        y=285,
                        t=0.3,
                        spots=[(1.3, 3.0, (0.33, 0.12, 0.66, 0.55))],
                    ),
                    dict(
                        img=IS + "exam_assessment.png",
                        scale=0.55,
                        x=262,
                        y=665,
                        t=1.2,
                        spots=[(3.0, 4.4, (0.0, 0.75, 0.99, 0.93))],
                    ),
                ],
            ),
            dict(
                img=IS + "exercise_variants.png",
                card=(1220, 770),
                kicker="03 · COMPOSE",
                title="Build the exam from exercise groups.",
                sub="Mix exercise types, mark groups optional and add variants.",
                cam=[(0, 0.6, 0.3, 0.8), (0.4, 0.6, 0.3, 0.8), (0.9, 0.6, 0.73, 0.8, ease_io5), (1, 0.6, 0.73, 0.8)],
                spots=[(0.8, 2.0, (0.73, 0.0, 0.995, 0.055))],
            ),
            dict(
                img=IS + "add_students.png",
                card=(1560, 746),
                kicker="04 · REGISTER",
                title="Register your students.",
                sub="See room, seat and progress of every student at a glance.",
                cam=[(0, 0.585, 0.3425, 0.83)],
                spots=[(0.8, 2.4, (0.66, 0.0, 0.995, 0.075)), (2.5, 4.4, (0.46, 0.14, 0.62, 0.68))],
            ),
            dict(
                img=IS + "test_run_management.png",
                card=(1560, 770),
                kicker="05 · REHEARSE",
                title="Rehearse with a test run.",
                sub="Try the exam as a student, then delete the test runs before it starts.",
                cam=[(0, 0.645, 0.3525, 0.71)],
                spots=[(0.8, 2.6, (0.295, 0.215, 0.99, 0.3)), (2.7, 4.4, (0.79, 0.375, 0.98, 0.44))],
            ),
            dict(
                img=IS + "individual_working_time.png",
                card=(1560, 770),
                kicker="06 · CONDUCT",
                title="Adjust the working time per student.",
                sub="Check the participation status of every single student exam.",
                cam=[(0, 0.43, 0.19, 0.55)],
                spots=[(0.8, 3.0, (0.17, 0.19, 0.46, 0.375))],
            ),
            dict(
                img=IS + "suspicious_behavior_analysis_results.png",
                card=(1560, 548),
                kicker="07 · MONITOR",
                title="Spot suspicious sessions.",
                sub="Different browser fingerprints and shared IP addresses are flagged.",
                cam=[(0, 0.5, 0.5, 1.03), (1, 0.5, 0.5, 1.0)],
                spots=[(0.8, 2.5, (0.125, 0.08, 0.995, 0.395)), (2.6, 4.4, (0.125, 0.405, 0.995, 0.99))],
            ),
            dict(
                img=IS + "exam_assessment_dashboard_second_correction.png",
                card=(1560, 619),
                kicker="08 · ASSESS",
                title="Correct in several rounds.",
                sub="Track every round per exercise and enable a second correction.",
                cam=[(0, 0.5, 0.515, 0.97)],
                spots=[(0.8, 2.5, (0.33, 0.07, 0.69, 0.93)), (2.6, 4.4, (0.775, 0.1, 0.935, 0.97))],
            ),
            dict(
                img=IS + "complaint_response.png",
                card=(1560, 425),
                kicker="09 · REVIEW",
                title="Answer every complaint.",
                sub="Update the assessment or reject the complaint with a response.",
                cam=[(0, 0.5, 0.5, 1.04), (1, 0.5, 0.5, 1.0)],
                spots=[(0.8, 2.4, (0.0, 0.2, 0.49, 0.75)), (2.5, 4.4, (0.5, 0.25, 0.995, 0.9))],
            ),
            dict(
                img=IS + "grade_key_editor.png",
                layout="side",
                card=(1060, 760),
                card_in="right",
                kicker="10 · GRADE",
                title="Turn points into grades.",
                sub="Define the grading key as intervals, including the plagiarism and no-participation grades.",
                cam=[(0, 0.64, 0.4815, 0.7)],
                spots=[(0.8, 2.4, (0.295, 0.135, 0.99, 0.23)), (2.5, 4.4, (0.3, 0.43, 0.86, 0.58))],
            ),
            dict(
                img=IS + "exam_statistics.png",
                kicker="11 · ANALYSE",
                title="Learn from the results.",
                sub="Averages, medians and score distributions of the exam and its exercise groups.",
                cam=[(0, 0.45, 0.22, 0.62), (0.45, 0.45, 0.22, 0.62), (0.85, 0.57, 0.55, 0.8, ease_io5), (1, 0.57, 0.55, 0.8)],
                spots=[(0.8, 2.2, (0.17, 0.125, 0.69, 0.365)), (3.2, 4.5, (0.17, 0.415, 0.97, 0.725))],
            ),
        ],
        dict(title="Artemis Exams", sub="Create, run and grade exams — end to end."),
    )


CUTS = {
    "student": (student, Path(DOCS) / "student" / "assets" / "exams" / "trailer-student.mp4"),
    "instructor": (instructor, Path(DOCS) / "instructor" / "exams" / "assets" / "trailer-instructor.mp4"),
}
