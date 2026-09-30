import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, studentFour, studentOne, studentThree, studentTwo } from '../../support/users';
import { generateUUID } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';

const course = { id: SEED_COURSES.examParticipation.id } as any;
const students = [studentOne, studentTwo, studentThree, studentFour];

/**
 * Every student gets an individual exam: a mandatory group is part of every exam, the other groups compete for the remaining places, and a group
 * with several exercises contributes exactly one of them. Which exercise a student gets is random, so the test checks the rules that hold for
 * every outcome, and that what a student sees in the exam is what the server assigned.
 */
test.describe('Exam individual exams', { tag: '@slow' }, () => {
    let exam: Exam;

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    test('Each student gets the mandatory group and one exercise of one optional group, and sees exactly these', async ({
        page,
        login,
        examAPIRequests,
        exerciseAPIRequests,
        examParticipation,
        examNavigation,
    }) => {
        await login(admin);
        // Two of three groups are assigned (20 points): the mandatory group and one of the two optional groups.
        exam = await examAPIRequests.createRunningExam({ course, numberOfExercisesInExam: 2, examMaxPoints: 20, randomizeExerciseOrder: true });
        expect((await examAPIRequests.getExam(exam)).randomizeExerciseOrder, 'the server stored the random order').toBe(true);
        const uid = generateUUID();
        const mandatoryGroup = await examAPIRequests.addExerciseGroupForExam(exam, 'Mandatory ' + uid, true);
        const variantsGroup = await examAPIRequests.addExerciseGroupForExam(exam, 'Variants ' + uid, false);
        const singleGroup = await examAPIRequests.addExerciseGroupForExam(exam, 'Single ' + uid, false);
        const mandatoryExercise = await exerciseAPIRequests.createTextExercise({ exerciseGroup: mandatoryGroup });
        const variants = [];
        for (let variant = 1; variant <= 3; variant++) {
            variants.push(await exerciseAPIRequests.createTextExercise({ exerciseGroup: variantsGroup }, `Variant ${variant} ${uid}`));
        }
        const singleExercise = await exerciseAPIRequests.createTextExercise({ exerciseGroup: singleGroup });
        const titleOfGroup = new Map<number, string>([
            [mandatoryExercise.id!, mandatoryGroup.title!],
            ...variants.map((variant) => [variant.id!, variantsGroup.title!] as [number, string]),
            [singleExercise.id!, singleGroup.title!],
        ]);
        const variantIds = variants.map((variant) => variant.id!);

        for (const student of students) {
            await examAPIRequests.registerStudentForExam(exam, student);
        }
        await examAPIRequests.generateMissingIndividualExams(exam);

        // The rules hold for every student exam, whichever exercises the server picked.
        const listed = await examAPIRequests.getAllStudentExams(exam);
        expect(listed).toHaveLength(students.length);
        const studentExams: { id: number; workingTime?: number; exercises?: { id?: number }[] }[] = [];
        for (const entry of listed) {
            studentExams.push(((await examAPIRequests.getStudentExam(exam, entry.id)) as unknown as { studentExam: (typeof studentExams)[number] }).studentExam);
        }
        for (const studentExam of studentExams) {
            const assigned: number[] = (studentExam.exercises ?? []).map((exercise) => exercise.id!);
            const who = `student exam ${studentExam.id}`;
            expect(assigned, `${who} gets two exercises`).toHaveLength(2);
            expect(new Set(assigned).size, `${who} gets no exercise twice`).toBe(2);
            expect(assigned, `${who} gets the mandatory exercise`).toContain(mandatoryExercise.id);
            const others = assigned.filter((id) => id !== mandatoryExercise.id);
            const fromVariants = others.filter((id) => variantIds.includes(id));
            expect(
                others.length === 1 && (fromVariants.length === 1 || others[0] === singleExercise.id),
                `${who} gets one exercise of one optional group, but got ${JSON.stringify(others)}`,
            ).toBe(true);
            expect(studentExam.workingTime, `${who} works as long as the exam lasts`).toBe((await examAPIRequests.getExam(exam)).workingTime);
        }

        // The student finds exactly the assigned groups in the exam and none of the others.
        await examAPIRequests.prepareExerciseStartForExam(exam);
        await login(studentOne);
        const ownId = await examAPIRequests.getOwnStudentExamId(exam);
        const own = studentExams.find((studentExam) => studentExam.id === ownId);
        expect(own, 'the student exam of the student is among those of the exam').toBeDefined();
        const ownGroups = new Set<string>(own!.exercises!.map((exercise) => titleOfGroup.get(exercise.id!)!));
        await examParticipation.startParticipation(studentOne, course, exam);
        for (const title of [mandatoryGroup.title!, variantsGroup.title!, singleGroup.title!]) {
            if (ownGroups.has(title)) {
                await examNavigation.openOrSaveExerciseByTitle(title);
                await expect(page.getByText(title).first()).toBeVisible();
            } else {
                await expect(page.getByText(title), `${title} is not part of the exam of ${studentOne.username}`).toHaveCount(0);
            }
        }
        expect(ownGroups.size).toBe(2);
    });
});
