import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor, studentFour, studentOne, studentThree, studentTwo } from '../../support/users';
import { makeExamSubmission, prepareRunningTextExam } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';

const course = { id: SEED_COURSES.examManagement.id } as any;

/**
 * After an exam, the instructor assesses what students did not hand in: the exercises of exams that were never handed in and the empty submissions of
 * exams that were handed in get zero points with a feedback that says why. Work that was handed in is left for the tutors.
 */
test.describe('Exam assessment of unsubmitted and empty work', { tag: '@slow' }, () => {
    let exam: Exam;

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    test('Exams that were not handed in and empty submissions get zero points, handed in work is left alone', async ({
        page,
        login,
        examAPIRequests,
        exerciseAPIRequests,
        examParticipation,
        examNavigation,
        examStartEnd,
        examDetails,
    }) => {
        test.slow();
        await login(admin);
        const prepared = await prepareRunningTextExam(examAPIRequests, exerciseAPIRequests, {
            course,
            students: [studentOne, studentTwo, studentThree, studentFour],
        });
        exam = prepared.exam;
        const { exercise, exerciseGroup } = prepared;

        // One: hands in an answer. Four: hands in without having written anything.
        const exerciseToSolve = { ...exercise, exerciseGroup, additionalData: { textFixture: 'loremIpsum-short.txt' } } as unknown as Exercise;
        await makeExamSubmission(course, exam, exerciseToSolve, page, examParticipation, examNavigation, examStartEnd);
        await examParticipation.startParticipation(studentFour, course, exam);
        await examNavigation.handInEarly();
        await examStartEnd.finishExam();
        // Two: starts the exam and writes an answer, but never hands in. Three: never starts.
        await login(studentTwo);
        const requestsOfTwo = new ExamAPIRequests(page);
        await requestsOfTwo.getOwnStudentExamForConduction(exam);
        const draft = await page.request.post(`api/text/exercises/${exercise.id}/text-submissions`, {
            data: { text: 'A draft that was never handed in', submitted: false, language: 'ENGLISH' },
        });
        expect(draft.status()).toBe(200);

        await login(admin);
        await examAPIRequests.concludeExam(exam, { publishResults: false });
        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/assessment-dashboard`);
        const assessed = page.waitForResponse((response) => response.url().includes('assess-unsubmitted-and-empty-student-exams'));
        await examDetails.clickAssessUnsubmittedParticipations();
        expect((await assessed).status()).toBe(200);

        // What the server holds per student: the submission of the exercise and the results it has.
        await login(admin);
        const outcomeOfAll = async () => {
            const outcome: Record<string, { submitted: boolean; text?: string; submissionSubmitted: boolean; scores: number[] }> = {};
            for (const studentExam of (await examAPIRequests.getAllStudentExams(exam)) as StudentExam[]) {
                const { studentExam: detail } = await examAPIRequests.getGradeSummary(exam, studentExam);
                const submission = detail.exercises[0].studentParticipations[0].submissions[0];
                outcome[detail.user.login] = {
                    submitted: detail.submitted,
                    text: submission.text,
                    submissionSubmitted: submission.submitted,
                    scores: (submission.results ?? []).map((result: { score: number }) => result.score),
                };
            }
            return outcome;
        };
        const outcome = await outcomeOfAll();

        // Handed in with an answer: left for the tutors, without a result.
        expect(outcome[studentOne.username]).toMatchObject({ submitted: true, scores: [] });
        expect(outcome[studentOne.username].text).toBeTruthy();
        // Never handed in, with an answer: zero points, and the answer is kept and counted as submitted.
        expect(outcome[studentTwo.username]).toEqual({ submitted: false, text: 'A draft that was never handed in', submissionSubmitted: true, scores: [0] });
        // Never started: zero points for the empty submission.
        expect(outcome[studentThree.username]).toMatchObject({ submitted: false, submissionSubmitted: true, scores: [0] });
        expect(outcome[studentThree.username].text).toBeUndefined();
        // Handed in without an answer: zero points for the empty submission.
        expect(outcome[studentFour.username]).toMatchObject({ submitted: true, submissionSubmitted: true, scores: [0] });
        expect(outcome[studentFour.username].text).toBeUndefined();

        // Running it a second time adds no further results.
        const again = await page.request.post(`api/exam/courses/${course.id}/exams/${exam.id}/student-exams/assess-unsubmitted-and-empty-student-exams`);
        expect(again.status()).toBe(200);
        expect(await outcomeOfAll(), 'a second run changes nothing').toEqual(outcome);
    });
});
