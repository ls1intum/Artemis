import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { admin, instructor } from '../../support/users';
import { generateUUID } from '../../support/utils';
import { test } from '../../support/fixtures';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { SEED_COURSES } from '../../support/seedData';

/**
 * Verifies the per-field validation messages of the exam exercise import table (PR #12940).
 *
 * Previously an invalid exercise-group title or non-programming exercise title only turned the input red,
 * with no explanation: the instructor pressed "import" and got a single generic alert. The import table now
 * renders a precise, actionable message directly below each invalid field and updates it live as the user
 * types (no submit needed). This test drives the same-course import flow and asserts those messages appear
 * for an empty exercise-group title and an empty non-programming exercise title, and disappear once a valid
 * value is entered again.
 *
 * The live "already exists in the target course" validation for programming exercise titles / short names is
 * covered by the component's unit tests (exam-exercise-import.component.spec.ts), which can exercise it in
 * isolation without provisioning two programming exercises across courses.
 */

const course = { id: SEED_COURSES.examManagement.id } as any;

test.describe('Exam import validation messages', { tag: '@fast' }, () => {
    let exam: Exam;
    let exerciseGroup: ExerciseGroup;
    let textExercise: TextExercise;

    test.beforeEach('Create an exam with a text exercise to import', async ({ login, examAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        exam = await examAPIRequests.createExam({ course, title: 'Import Validation ' + generateUUID() });
        exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam, 'Group ' + generateUUID());
        textExercise = await exerciseAPIRequests.createTextExercise({ exerciseGroup }, 'Text ' + generateUUID());
    });

    test('shows actionable error messages below invalid fields during exam import', async ({ login, page }) => {
        // Importing the exam into its own course renders the exercise-import table with editable group / exercise fields.
        await login(instructor, `/course-management/${course.id}/exams/import/${exam.id}`);

        const groupTitleInput = page.locator(`#exerciseGroup-${exerciseGroup.id}-title`);
        const groupTitleError = page.locator(`#exerciseGroup-${exerciseGroup.id}-title ~ .invalid-feedback`);
        const textTitleInput = page.locator(`#exercise-${textExercise.id}-title`);
        const textTitleError = page.locator(`#exercise-${textExercise.id}-title ~ .invalid-feedback`);

        // Wait for the import table to render with the seeded (valid) values -> no messages yet.
        await expect(groupTitleInput).toBeVisible();
        await expect(groupTitleError).toBeHidden();
        await expect(textTitleError).toBeHidden();

        // Clearing the exercise-group title shows the specific message right below the field.
        await groupTitleInput.fill('');
        await expect(groupTitleError).toBeVisible();
        await expect(groupTitleError).toContainText('title for the exercise group');

        // Re-entering a title clears the message live, without pressing import.
        await groupTitleInput.fill('Group ' + generateUUID());
        await expect(groupTitleError).toBeHidden();

        // Clearing a non-programming exercise title shows the "title required" message right below the field.
        await textTitleInput.fill('');
        await expect(textTitleError).toBeVisible();
        await expect(textTitleError).toContainText('Please enter a title');

        // Re-entering a title clears the message live.
        await textTitleInput.fill('Text ' + generateUUID());
        await expect(textTitleError).toBeHidden();
    });
});

/**
 * End-to-end coverage of the live programming-exercise clash detection (PR #12940) plus a full, successful exam import.
 *
 * The target course already contains a programming exercise; the exam we import (from a different course) contains a
 * programming exercise with the SAME title and short name. Because the import table now fetches the target course's
 * already-used programming titles / short names, the clash is flagged directly below the fields the moment the table
 * loads, before any submit. After renaming to unique values the messages clear, and the import then runs to completion
 * (including the real programming-repository copy), which proves the import works end to end.
 */
const sourceCourse = { id: SEED_COURSES.exerciseManagement.id } as any;
const targetCourse = { id: SEED_COURSES.import.id } as any;

test.describe('Exam import with a clashing programming exercise', { tag: '@slow' }, () => {
    let sourceExam: Exam;
    let programmingExercise: ProgrammingExercise;
    let clashTitle: string;
    let clashShortName: string;

    test.beforeEach(
        'Create a target-course programming exercise and a source exam whose programming exercise clashes with it',
        async ({ login, examAPIRequests, exerciseAPIRequests }) => {
            await login(admin);
            const uuid = generateUUID();
            clashTitle = 'Clash ' + uuid;
            clashShortName = 'clash' + uuid;

            // The target course already contains a programming exercise with this title / short name ...
            await exerciseAPIRequests.createProgrammingExercise({ course: targetCourse, title: clashTitle, programmingShortName: clashShortName });

            // ... and the exam we will import has a programming exercise with the SAME title / short name (in a different
            // course, so both can be created). Importing it into the target course is exactly the clash the live validation catches.
            sourceExam = await examAPIRequests.createExam({ course: sourceCourse, title: 'Import Source ' + uuid });
            const exerciseGroup = await examAPIRequests.addExerciseGroupForExam(sourceExam, 'Group ' + uuid);
            programmingExercise = await exerciseAPIRequests.createProgrammingExercise({ exerciseGroup, title: clashTitle, programmingShortName: clashShortName });
        },
    );

    test('flags the clashing programming title / short name live, then imports successfully after renaming', async ({ login, page, examCreation, examAPIRequests }) => {
        test.slow();
        await login(instructor, `/course-management/${targetCourse.id}/exams/import/${sourceExam.id}`);

        const titleInput = page.locator(`#exercise-${programmingExercise.id}-title`);
        const titleError = page.locator(`#exercise-${programmingExercise.id}-title ~ .invalid-feedback`);
        const shortNameInput = page.locator(`#programming-exercise-${programmingExercise.id}-shortName`);
        const shortNameError = page.locator(`#programming-exercise-${programmingExercise.id}-shortName ~ .invalid-feedback`);

        await expect(titleInput).toBeVisible();

        // The titles / short names fetched from the target course flag the import exercise before any submit.
        await expect(titleError).toBeVisible();
        await expect(titleError).toContainText('already exists in the target course');
        await expect(shortNameError).toBeVisible();
        await expect(shortNameError).toContainText('already exists in the target course');

        // Renaming to values not used in the target course clears both messages live (no submit needed).
        const uuid = generateUUID();
        const newTitle = 'Imported Prog ' + uuid;
        const newShortName = 'improg' + uuid;
        await titleInput.fill(newTitle);
        await shortNameInput.fill(newShortName);
        await expect(titleError).toBeHidden();
        await expect(shortNameError).toBeHidden();

        // Provide the exam conduction dates that are intentionally stripped for an import, so the form becomes valid.
        await examCreation.setVisibleDate(dayjs());
        await examCreation.setStartDate(dayjs().add(1, 'hour'));
        await examCreation.setEndDate(dayjs().add(2, 'hour'));

        // Run the import and wait for the (programming-repository-copying) server call to finish.
        await expect(page.locator('#save-exam')).toBeEnabled();
        const importResponsePromise = page.waitForResponse((response) => response.url().includes('/exam-import') && response.request().method() === 'POST', { timeout: 180000 });
        await page.locator('#save-exam').click();
        const importResponse = await importResponsePromise;
        // The import endpoint creates the exam, so it answers 201 Created.
        expect(importResponse.ok()).toBeTruthy();

        // The progress dialog shows the success summary and must be dismissed before navigating on.
        const dismissButton = page.locator('#exam-import-progress-dismiss');
        await expect(dismissButton).toBeVisible({ timeout: 60000 });
        await expect(page.getByText(/imported successfully/i)).toBeVisible();
        await dismissButton.click();

        // We land on the detail page of the freshly imported exam.
        await page.waitForURL(new RegExp(`/course-management/${targetCourse.id}/exams/\\d+$`), { timeout: 30000 });
        const importedExamId = Number(page.url().split('/').pop());
        expect(importedExamId).not.toBe(sourceExam.id);

        // The imported exam really contains the (renamed) programming exercise -> the import worked end to end.
        const groups = await examAPIRequests.getExerciseGroups({ id: importedExamId, course: targetCourse } as Exam);
        const importedExercises = groups.flatMap((group) => group.exercises ?? []);
        expect(importedExercises.some((exercise) => exercise.title === newTitle)).toBe(true);
    });
});
