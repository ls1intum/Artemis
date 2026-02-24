import { test } from '../../support/fixtures';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { expect } from '@playwright/test';
import { admin, studentTwo, instructor } from '../../support/users';
import { generateUUID } from '../../support/utils';
import dayjs from 'dayjs';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Visibility } from 'app/programming/shared/entities/programming-exercise-test-case.model';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';

/**
 * CRITICAL REGRESSION TEST: PlantUML diagram isolation in exam mode.
 *
 * This test guards against a production bug where PlantUML diagrams from one programming exercise
 * were shown in a different exercise during exams. The root cause was that the PlantUML extension
 * is a root-level singleton, and all exercises shared the same container IDs (plantUml-0, plantUml-1, ...),
 * causing document.getElementById() to return the wrong container.
 *
 * The fix scopes container IDs per exercise: plantUml-{exerciseId}-{index}.
 *
 * This test creates 3 programming exercises with DISTINCT PlantUML diagrams (different class names)
 * and verifies that each exercise renders its OWN diagram, not another exercise's diagram.
 *
 * Exercise A: 1 diagram containing "ClassAlpha"
 * Exercise B: 1 diagram containing "ClassBeta"
 * Exercise C: 2 diagrams containing "ClassGamma" and "ClassDelta"
 */

// Each exercise gets a unique PlantUML diagram with a distinguishable class name.
// These are intentionally simple (no testsColor) to produce clean, predictable SVGs.
const problemStatementA = '# Exercise Alpha\n\nThis exercise covers basic concepts.\n\n@startuml\nclass ClassAlpha {\n+doAlpha()\n}\n@enduml\n';

const problemStatementB = '# Exercise Beta\n\nThis exercise covers intermediate concepts.\n\n@startuml\nclass ClassBeta {\n+doBeta()\n}\n@enduml\n';

const problemStatementC =
    '# Exercise Gamma\n\nFirst diagram:\n\n@startuml\nclass ClassGamma {\n+doGamma()\n}\n@enduml\n\nSecond diagram:\n\n@startuml\nclass ClassDelta {\n+doDelta()\n}\n@enduml\n';

// The unique identifiers that MUST appear only in their respective exercise's SVGs
const IDENTIFIER_A = 'ClassAlpha';
const IDENTIFIER_B = 'ClassBeta';
const IDENTIFIER_C0 = 'ClassGamma';
const IDENTIFIER_C1 = 'ClassDelta';

test.describe('Exam PlantUML diagram isolation', { tag: '@slow' }, () => {
    let course: Course;
    let exam: Exam;
    let exerciseA: ProgrammingExercise;
    let exerciseB: ProgrammingExercise;
    let exerciseC: ProgrammingExercise;
    let groupTitleA: string;
    let groupTitleB: string;
    let groupTitleC: string;

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
        await courseManagementAPIRequests.addStudentToCourse(course, studentTwo);
        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
    });

    test.beforeEach('Create exam with 3 programming exercises', async ({ login, examAPIRequests, exerciseAPIRequests }) => {
        await login(admin);

        exam = await createExam(course, examAPIRequests, {
            title: 'PlantUML Isolation ' + generateUUID(),
            examMaxPoints: 30,
            numberOfExercisesInExam: 3,
        });

        // Create 3 exercise groups with programming exercises, each with a DIFFERENT PlantUML diagram.
        // The distinct class names in the diagrams allow us to detect cross-contamination.
        groupTitleA = 'Group Alpha ' + generateUUID();
        const exerciseGroupA = await examAPIRequests.addExerciseGroupForExam(exam, groupTitleA);
        exerciseA = await exerciseAPIRequests.createProgrammingExercise({
            exerciseGroup: exerciseGroupA,
            title: 'Exercise Alpha ' + generateUUID(),
            problemStatement: problemStatementA,
        });

        groupTitleB = 'Group Beta ' + generateUUID();
        const exerciseGroupB = await examAPIRequests.addExerciseGroupForExam(exam, groupTitleB);
        exerciseB = await exerciseAPIRequests.createProgrammingExercise({
            exerciseGroup: exerciseGroupB,
            title: 'Exercise Beta ' + generateUUID(),
            problemStatement: problemStatementB,
        });

        groupTitleC = 'Group Gamma ' + generateUUID();
        const exerciseGroupC = await examAPIRequests.addExerciseGroupForExam(exam, groupTitleC);
        exerciseC = await exerciseAPIRequests.createProgrammingExercise({
            exerciseGroup: exerciseGroupC,
            title: 'Exercise Gamma ' + generateUUID(),
            problemStatement: problemStatementC,
        });

        // Wait for CI pipelines to complete by checking test case availability
        await exerciseAPIRequests.changeProgrammingExerciseTestVisibility(exerciseA, Visibility.Always, 0);
        await exerciseAPIRequests.changeProgrammingExerciseTestVisibility(exerciseB, Visibility.Always, 0);
        await exerciseAPIRequests.changeProgrammingExerciseTestVisibility(exerciseC, Visibility.Always, 0);

        await examAPIRequests.registerStudentForExam(exam, studentTwo);
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);
    });

    /**
     * Core regression test: verifies that each exercise renders its own PlantUML diagram.
     *
     * In exam mode, all 3 exercise components coexist in the DOM simultaneously (hidden via [hidden]).
     * They share a single PlantUML extension singleton. This test verifies:
     *
     * 1) Container IDs are exercise-scoped: plantUml-{exerciseId}-{index}
     * 2) Each container has a rendered SVG (not empty)
     * 3) Each SVG contains ONLY its own exercise's class names (no cross-contamination)
     * 4) Exercise C has exactly 2 PlantUML diagrams (multi-diagram support)
     */
    test('Each exercise renders its own PlantUML diagram without cross-contamination', async ({ page, examParticipation, examNavigation }) => {
        // Start the exam as a student - this loads all exercise components into the DOM
        await examParticipation.startParticipation(studentTwo, course, exam);

        // Navigate to each exercise to trigger rendering.
        // In exam mode, all exercises stay in the DOM (hidden), so visiting each one
        // ensures the component initializes and fires its PlantUML render cycle.
        await examNavigation.openOrSaveExerciseByTitle(groupTitleA);
        await examNavigation.openOrSaveExerciseByTitle(groupTitleB);
        await examNavigation.openOrSaveExerciseByTitle(groupTitleC);

        // Give time for all async PlantUML SVG HTTP requests to complete.
        // The server renders PlantUML source to SVG; this is an async operation per diagram.
        // With 4 diagrams total (1+1+2) and server-side rendering, allow generous timeout.

        // --- Exercise A: 1 diagram with ClassAlpha ---
        const containerA = page.locator(`#plantUml-${exerciseA.id}-0`);
        await expect(containerA).toBeAttached({ timeout: 30000 });
        const svgA = containerA.locator('svg');
        await expect(svgA).toBeAttached({ timeout: 30000 });
        const svgTextA = await svgA.textContent();
        expect(svgTextA).toContain(IDENTIFIER_A);
        // Cross-contamination check: must NOT contain other exercises' identifiers
        expect(svgTextA).not.toContain(IDENTIFIER_B);
        expect(svgTextA).not.toContain(IDENTIFIER_C0);
        expect(svgTextA).not.toContain(IDENTIFIER_C1);
        // No second diagram should exist for exercise A
        await expect(page.locator(`#plantUml-${exerciseA.id}-1`)).not.toBeAttached();

        // --- Exercise B: 1 diagram with ClassBeta ---
        const containerB = page.locator(`#plantUml-${exerciseB.id}-0`);
        await expect(containerB).toBeAttached({ timeout: 30000 });
        const svgB = containerB.locator('svg');
        await expect(svgB).toBeAttached({ timeout: 30000 });
        const svgTextB = await svgB.textContent();
        expect(svgTextB).toContain(IDENTIFIER_B);
        expect(svgTextB).not.toContain(IDENTIFIER_A);
        expect(svgTextB).not.toContain(IDENTIFIER_C0);
        expect(svgTextB).not.toContain(IDENTIFIER_C1);
        await expect(page.locator(`#plantUml-${exerciseB.id}-1`)).not.toBeAttached();

        // --- Exercise C: 2 diagrams with ClassGamma and ClassDelta ---
        const containerC0 = page.locator(`#plantUml-${exerciseC.id}-0`);
        await expect(containerC0).toBeAttached({ timeout: 30000 });
        const svgC0 = containerC0.locator('svg');
        await expect(svgC0).toBeAttached({ timeout: 30000 });
        const svgTextC0 = await svgC0.textContent();
        expect(svgTextC0).toContain(IDENTIFIER_C0);
        expect(svgTextC0).not.toContain(IDENTIFIER_A);
        expect(svgTextC0).not.toContain(IDENTIFIER_B);
        expect(svgTextC0).not.toContain(IDENTIFIER_C1);

        const containerC1 = page.locator(`#plantUml-${exerciseC.id}-1`);
        await expect(containerC1).toBeAttached({ timeout: 30000 });
        const svgC1 = containerC1.locator('svg');
        await expect(svgC1).toBeAttached({ timeout: 30000 });
        const svgTextC1 = await svgC1.textContent();
        expect(svgTextC1).toContain(IDENTIFIER_C1);
        expect(svgTextC1).not.toContain(IDENTIFIER_A);
        expect(svgTextC1).not.toContain(IDENTIFIER_B);
        expect(svgTextC1).not.toContain(IDENTIFIER_C0);

        // No third diagram should exist for exercise C
        await expect(page.locator(`#plantUml-${exerciseC.id}-2`)).not.toBeAttached();

        // --- Global uniqueness check ---
        // Verify that ALL PlantUML container IDs in the DOM are unique.
        // This catches the exact bug scenario: if IDs like plantUml-0 appear multiple times,
        // document.getElementById() would return the wrong element.
        const allPlantUmlContainers = page.locator('[id^="plantUml-"]');
        const count = await allPlantUmlContainers.count();
        expect(count).toBe(4); // 1 + 1 + 2 = 4 total diagrams

        const ids = new Set<string>();
        for (let i = 0; i < count; i++) {
            const id = await allPlantUmlContainers.nth(i).getAttribute('id');
            expect(id).not.toBeNull();
            ids.add(id!);
        }
        // All 4 IDs must be unique
        expect(ids.size).toBe(4);

        // Verify the IDs follow the expected exercise-scoped pattern
        expect(ids).toContain(`plantUml-${exerciseA.id}-0`);
        expect(ids).toContain(`plantUml-${exerciseB.id}-0`);
        expect(ids).toContain(`plantUml-${exerciseC.id}-0`);
        expect(ids).toContain(`plantUml-${exerciseC.id}-1`);
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});

async function createExam(course: Course, examAPIRequests: ExamAPIRequests, customExamConfig?: any) {
    const defaultExamConfig = {
        course,
        title: 'exam' + generateUUID(),
        visibleDate: dayjs().subtract(3, 'minutes'),
        startDate: dayjs().subtract(2, 'minutes'),
        endDate: dayjs().add(1, 'hour'),
        examMaxPoints: 10,
        numberOfExercisesInExam: 1,
    };
    const examConfig = { ...defaultExamConfig, ...customExamConfig };
    return await examAPIRequests.createExam(examConfig);
}
