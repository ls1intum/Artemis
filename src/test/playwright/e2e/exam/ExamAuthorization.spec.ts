import { expect, Page } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor, studentOne, studentTwo, tutor, UserCredentials } from '../../support/users';
import { asAdmin, asUser, prepareRunningTextExam } from '../../support/utils';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { ExerciseAPIRequests } from '../../support/requests/ExerciseAPIRequests';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';

const course = { id: SEED_COURSES.examManagement.id } as any;

type Call = { method: 'get' | 'post' | 'put' | 'patch' | 'delete'; path: string; data?: unknown; multipart?: Record<string, { name: string; mimeType: string; buffer: Buffer }> };

/**
 * Who may do what with an exam is decided by the server, whatever the client shows. Students and tutors call every endpoint that is reserved for
 * a higher role of a running exam with registered students and their work, and every call must be refused. The calls would delete the exam, end it,
 * change it or hand out its data if the check was missing, so the test also asserts that the exam is untouched afterwards. The instructor is the control:
 * the same exam answers the read calls.
 */
test.describe('Exam authorization', { tag: '@slow' }, () => {
    let exam: Exam;
    let exerciseGroup: ExerciseGroup;
    let exerciseId: number;
    let ownStudentExamId: number;
    let otherStudentExamId: number;
    let calls: { instructorOnly: Call[]; editorOnly: Call[]; tutorLevel: Call[] };

    test.afterAll('Delete the exam', async ({ browser }) => {
        await asAdmin(browser, (examAPIRequests) => examAPIRequests.deleteExam(exam));
    });

    test.beforeAll('Prepare a running exam with two registered students and describe the calls', async ({ browser }) => {
        await asAdmin(browser, async (examAPIRequests) => {
            const prepared = await prepareRunningTextExam(examAPIRequests, new ExerciseAPIRequests(examAPIRequests.page), { course, students: [studentOne, studentTwo] });
            exam = prepared.exam;
            exerciseGroup = prepared.exerciseGroup;
            exerciseId = prepared.exercise.id!;
        });
        ownStudentExamId = await asUser(browser, studentOne, (requests) => requests.getOwnStudentExamId(exam));
        otherStudentExamId = await asUser(browser, studentTwo, (requests) => requests.getOwnStudentExamId(exam));
        const base = `api/exam/courses/${course.id}`;
        const examBase = `${base}/exams/${exam.id}`;
        const examBody = { ...exam, id: undefined };
        const group = { title: 'Forbidden group', isMandatory: true, exam: { id: exam.id } };
        calls = {
            instructorOnly: [
                { method: 'post', path: `${base}/exams`, data: examBody },
                { method: 'put', path: `${base}/exams`, data: exam },
                { method: 'patch', path: `${examBase}/working-time`, data: 60 },
                { method: 'post', path: `${examBase}/announcements`, data: 'Forbidden announcement' },
                { method: 'post', path: `${base}/exam-import`, data: exam },
                { method: 'delete', path: `${examBase}/cleanup` },
                { method: 'delete', path: examBase },
                { method: 'delete', path: `${examBase}/reset` },
                { method: 'post', path: `${examBase}/generate-student-exams` },
                { method: 'post', path: `${examBase}/generate-missing-student-exams` },
                { method: 'post', path: `${examBase}/student-exams/evaluate-quiz-exercises` },
                { method: 'post', path: `${examBase}/students`, data: [{ login: tutor.username }] },
                { method: 'post', path: `${examBase}/register-course-students` },
                { method: 'delete', path: `${examBase}/students/${studentOne.username}` },
                { method: 'delete', path: `${examBase}/students` },
                { method: 'put', path: `${examBase}/archive` },
                { method: 'get', path: `${examBase}/download-archive` },
                { method: 'get', path: `${examBase}/scores` },
                {
                    method: 'get',
                    path: `${examBase}/suspicious-sessions?differentStudentExamsSameIPAddress=true&differentStudentExamsSameBrowserFingerprint=true&sameStudentExamDifferentIPAddresses=true&sameStudentExamDifferentBrowserFingerprints=true&ipOutsideOfRange=false`,
                },
                { method: 'get', path: `${examBase}/deletion-summary` },
                { method: 'get', path: `${examBase}/locked-submissions` },
                { method: 'get', path: `${examBase}/exercises-with-potential-plagiarism` },
                { method: 'get', path: `${examBase}/student-exams` },
                { method: 'get', path: `${examBase}/student-exams/${ownStudentExamId}` },
                { method: 'patch', path: `${examBase}/student-exams/${ownStudentExamId}/working-time`, data: 60 },
                { method: 'post', path: `${examBase}/student-exams/assess-unsubmitted-and-empty-student-exams` },
                { method: 'post', path: `${examBase}/student-exams/start-exercises` },
                { method: 'get', path: `${examBase}/student-exams/start-exercises/status` },
                { method: 'put', path: `${examBase}/student-exams/${ownStudentExamId}/toggle-to-submitted` },
                { method: 'put', path: `${examBase}/student-exams/${ownStudentExamId}/toggle-to-unsubmitted` },
                { method: 'get', path: `${examBase}/test-runs` },
                { method: 'post', path: `${examBase}/test-runs`, data: {} },
                { method: 'get', path: `${examBase}/export-students` },
                { method: 'get', path: `${examBase}/verify-exam-users` },
                { method: 'get', path: `${examBase}/students/search?searchTerm=student` },
                {
                    method: 'post',
                    path: `${examBase}/exam-users-save-images`,
                    multipart: { file: { name: 'images.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4') } },
                },
            ],
            tutorLevel: [
                { method: 'get', path: `${examBase}/statistics` },
                { method: 'get', path: `${examBase}/exam-for-assessment-dashboard` },
                { method: 'get', path: `${examBase}/stats-for-exam-assessment-dashboard` },
                { method: 'get', path: `${examBase}/latest-end-date` },
                { method: 'get', path: `${base}/exams` },
                { method: 'get', path: `${examBase}/exam-students/paged?page=0&pageSize=10&sortingOrder=ASCENDING&sortedColumn=name&searchTerm=` },
                { method: 'post', path: `${examBase}/students/${studentOne.username}/attendance-check`, data: 'Forbidden check' },
                {
                    method: 'post',
                    path: `${examBase}/exam-users`,
                    multipart: { examUserDTO: { name: 'examUserDTO', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify({ login: studentOne.username })) } },
                },
            ],
            editorOnly: [
                { method: 'post', path: `${examBase}/exercise-groups`, data: group },
                { method: 'put', path: `${examBase}/exercise-groups`, data: { ...group, id: exerciseGroup.id } },
                { method: 'put', path: `${examBase}/exercises/${exerciseId}/exercise-group`, data: { exerciseGroupId: exerciseGroup.id } },
                { method: 'post', path: `${examBase}/import-exercise-group`, data: [] },
                { method: 'put', path: `${examBase}/exercise-groups-order`, data: [exerciseGroup.id] },
                { method: 'delete', path: `${examBase}/exercise-groups/${exerciseGroup.id}` },
            ],
        };
    });

    /** Makes every call as the given user and returns the ones that were not refused. */
    async function notRefused(page: Page, calls: Call[]) {
        const answered: string[] = [];
        for (const call of calls) {
            const response = await page.request[call.method](call.path, call.multipart ? { multipart: call.multipart } : call.data !== undefined ? { data: call.data } : {});
            if (response.status() !== 403) {
                answered.push(`${call.method.toUpperCase()} ${call.path} answered ${response.status()}`);
            }
        }
        return answered;
    }

    /** The exam and the work of the students are exactly as they were prepared. */
    async function expectExamUntouched(examAPIRequests: ExamAPIRequests) {
        const stored = await examAPIRequests.getExam(exam);
        expect(stored.title).toBe(exam.title);
        expect((stored as { id?: number }).id).toBe(exam.id);
        const studentExams = await examAPIRequests.getAllStudentExams(exam);
        expect(studentExams, 'both student exams are still there').toHaveLength(2);
        expect(
            studentExams.map((studentExam: StudentExam) => studentExam.submitted),
            'nobody was submitted or un-submitted',
        ).toEqual([false, false]);
        const groups = await examAPIRequests.getExerciseGroups(exam);
        expect(
            groups.map((group) => group.id),
            'the exercise group is unchanged',
        ).toEqual([exerciseGroup.id]);
        expect(groups[0].title).toBe(exerciseGroup.title);
    }

    for (const [name, user] of [
        ['A student', studentOne],
        ['A tutor', tutor],
    ] as [string, UserCredentials][]) {
        test(`${name} is refused by every endpoint that is reserved for instructors and editors`, async ({ page, login, examAPIRequests }) => {
            await login(user);
            const answered = await notRefused(page, [...calls.instructorOnly, ...calls.editorOnly]);
            expect(answered, 'every call must be answered with 403').toEqual([]);
            await login(admin);
            await expectExamUntouched(examAPIRequests);
        });
    }

    test('A student is refused by the endpoints for tutors, and a tutor is served by them', async ({ page, login }) => {
        await login(studentOne);
        expect(await notRefused(page, calls.tutorLevel), 'every call must be answered with 403').toEqual([]);
        await login(tutor);
        // The assessment dashboards only open once the exam is over, which a running exam is not.
        for (const call of calls.tutorLevel.filter((candidate) => candidate.method === 'get' && !candidate.path.includes('assessment-dashboard'))) {
            const response = await page.request.get(call.path);
            expect(response.status(), `a tutor may read ${call.path}`).toBe(200);
        }
    });

    test('A student can not read the exam of another student', async ({ page, login, examAPIRequests }) => {
        const examBase = `api/exam/courses/${course.id}/exams/${exam.id}`;
        await login(studentTwo);
        const foreign = [
            { method: 'get', path: `${examBase}/student-exams/${ownStudentExamId}/conduction` },
            { method: 'get', path: `${examBase}/student-exams/${ownStudentExamId}/summary` },
            { method: 'get', path: `${examBase}/student-exams/${ownStudentExamId}/grade-summary` },
        ] as Call[];
        expect(await notRefused(page, foreign), 'the exam of another student is not handed out').toEqual([]);

        // Submitting for another student is refused, too, and does not touch their work.
        const submitted = await page.request.post(`${examBase}/student-exams/submit`, { data: { id: ownStudentExamId, exercises: [], submitted: true } });
        expect(submitted.status(), 'a student exam of another student can not be handed in').toBeGreaterThanOrEqual(400);
        expect(submitted.status()).toBeLessThan(500);
        await login(admin);
        const studentExams = await examAPIRequests.getAllStudentExams(exam);
        expect(studentExams.find((studentExam: StudentExam) => studentExam.id === ownStudentExamId)?.submitted).toBe(false);
        expect(studentExams.find((studentExam: StudentExam) => studentExam.id === otherStudentExamId)?.submitted).toBe(false);
    });

    test('The instructor, in contrast, is served by the same endpoints', async ({ page, login }) => {
        const examBase = `api/exam/courses/${course.id}/exams/${exam.id}`;
        await login(instructor);
        for (const path of [
            `${examBase}/scores`,
            `${examBase}/suspicious-sessions?differentStudentExamsSameIPAddress=true&differentStudentExamsSameBrowserFingerprint=true&sameStudentExamDifferentIPAddresses=true&sameStudentExamDifferentBrowserFingerprints=true&ipOutsideOfRange=false`,
            `${examBase}/deletion-summary`,
            `${examBase}/student-exams`,
            `${examBase}/export-students`,
        ]) {
            const response = await page.request.get(path);
            expect(response.status(), `the instructor may read ${path}`).toBe(200);
        }
    });
});

/**
 * Once results are published, a student reads their own summary and grade summary, and nobody else's: the endpoints hand the data out by the id of the
 * student exam, so the server has to compare the owner with the caller. The exams are handed in by the instructor, which is what makes the summary
 * available, and the results are published with the end of the exam.
 */
test.describe('Exam authorization of published results', { tag: '@slow' }, () => {
    let resultExam: Exam;
    let ownId: number;
    let otherId: number;

    test.beforeAll('Hand in and end an exam of two students with published results', async ({ browser }) => {
        await asAdmin(browser, async (examAPIRequests) => {
            const prepared = await prepareRunningTextExam(examAPIRequests, new ExerciseAPIRequests(examAPIRequests.page), { course, students: [studentOne, studentTwo] });
            resultExam = prepared.exam;
            // The student exams can be handed in by the instructor once the exam is over.
            await examAPIRequests.concludeExam(resultExam, { publishResults: true });
            for (const studentExam of (await examAPIRequests.getAllStudentExams(resultExam)) as StudentExam[]) {
                const toggled = await examAPIRequests.page.request.put(`api/exam/courses/${course.id}/exams/${resultExam.id}/student-exams/${studentExam.id}/toggle-to-submitted`);
                expect(toggled.status(), `hand in student exam ${studentExam.id}`).toBe(200);
            }
        });
        ownId = await asUser(browser, studentOne, (requests) => requests.getOwnStudentExamId(resultExam));
        otherId = await asUser(browser, studentTwo, (requests) => requests.getOwnStudentExamId(resultExam));
    });

    test.afterAll('Delete the exam', async ({ browser }) => {
        await asAdmin(browser, (examAPIRequests) => examAPIRequests.deleteExam(resultExam));
    });

    test('A student reads their own summary and grade summary, but not those of another student', async ({ page, login }) => {
        const examBase = `api/exam/courses/${course.id}/exams/${resultExam.id}`;
        await login(studentOne);
        for (const endpoint of ['summary', 'grade-summary']) {
            expect((await page.request.get(`${examBase}/student-exams/${ownId}/${endpoint}`)).status(), `the student reads their own ${endpoint}`).toBe(200);
            expect((await page.request.get(`${examBase}/student-exams/${otherId}/${endpoint}`)).status(), `the student must not read the ${endpoint} of another student`).toBe(403);
        }
    });
});
