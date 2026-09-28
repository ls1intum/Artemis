import { test } from '../../support/fixtures';
import dayjs from 'dayjs';
import { Course } from 'app/course/shared/entities/course.model';
import { admin, instructor, studentOne, studentThree, studentTwo, tutor } from '../../support/users';
import { base64StringToBlob, convertBooleanToCheckIconClass, dayjsToString, generateUUID, trimDate } from '../../support/utils';
import { expect } from '@playwright/test';
import { Fixtures } from '../../fixtures/fixtures';
import multipleChoiceQuizTemplate from '../../fixtures/exercise/quiz/multiple_choice/template.json';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { ProgrammingLanguage } from '../../support/constants';

// Common primitives
const courseData = {
    title: '',
    shortName: '',
    description: 'Lorem Ipsum',
    startDate: dayjs(),
    endDate: dayjs().add(1, 'day'),
    testCourse: true,
    semester: 'SS23',
    maxPoints: 40,
    programmingLanguage: 'JAVA',
    enableComplaints: true,
    maxComplaints: 5,
    maxTeamComplaints: 3,
    maxComplaintTimeDays: 6,
    enableMoreFeedback: true,
    maxRequestMoreFeedbackTimeDays: 4,
    presentationScoreEnabled: true,
    presentationScore: 10,
};

const editedCourseData = {
    title: '',
    testCourse: false,
};

const dateFormat = 'MMM D, YYYY HH:mm';

export interface CourseSummary {
    isTestCourse: boolean;
    students: number;
    tutors: number;
    editors: number;
    instructors: number;
    exams: number;
    lectures: number;
    programingExercises: number;
    modelingExercises: number;
    quizExercises: number;
    textExercises: number;
    fileUploadExercises: number;
    communicationPosts: number;
}

test.describe('Course management', { tag: '@fast' }, () => {
    test('keeps the course chart values inside the doughnut rings', async ({ page, login, courseManagementAPIRequests }) => {
        await login(admin);
        const course = await courseManagementAPIRequests.createCourse();
        try {
            await page.setViewportSize({ width: 1400, height: 1800 });
            await page.goto(`/course-management/${course.id}`);
            const charts = page.locator('jhi-course-detail-doughnut-chart');
            await expect(charts.first().getByTestId('course-chart-values')).toContainText('0%');
            for (const width of [1400, 800]) {
                await page.setViewportSize({ width, height: 1800 });
                for (const chart of await charts.all()) {
                    await expect
                        .poll(() =>
                            chart.evaluate((element) => {
                                const svg = element.querySelector<SVGSVGElement>('svg[role="img"]')!;
                                const canvas = svg.getBoundingClientRect();
                                const label = element.querySelector('[data-testid="course-chart-values"]')!.getBoundingClientRect();
                                const centerX = canvas.width / 2;
                                const centerY = canvas.height / 2;
                                // Measure the inner edge of the rendered ring, independently of its configured size.
                                const radii = Array.from(svg.querySelectorAll<SVGPathElement>('path[d]:not([d=""])')).flatMap((path) => {
                                    const length = path.getTotalLength();
                                    return Array.from({ length: 101 }, (_, index) => {
                                        const point = path.getPointAtLength((length * index) / 100).matrixTransform(path.getScreenCTM()!);
                                        return Math.hypot(point.x - canvas.x - centerX, point.y - canvas.y - centerY);
                                    });
                                });
                                if (!radii.length) {
                                    return false;
                                }
                                const innerRadius = Math.min(...radii);
                                return [label.left, label.right].every((x) =>
                                    [label.top, label.bottom].every((y) => Math.hypot(x - canvas.x - centerX, y - canvas.y - centerY) < innerRadius),
                                );
                            }),
                        )
                        .toBe(true);
                }
            }
        } finally {
            await courseManagementAPIRequests.deleteCourse(course, admin);
        }
    });

    test.describe('Manual student selection', () => {
        let course: Course;

        test.beforeEach('Create course', async ({ login, courseManagementAPIRequests }) => {
            await login(admin, '/');
            const uid = generateUUID();
            courseData.title = 'Course ' + uid;
            courseData.shortName = 'playwright' + uid;
            course = await courseManagementAPIRequests.createCourse({ courseName: courseData.title, courseShortName: courseData.shortName });
        });

        test('Manually adds and removes a student', async ({ navigationBar, courseManagement }) => {
            // 4 openCourseManagement + 4 openCourse navigations + add/remove flows easily
            // exceed the @fast 60s budget under heavy multi-node load. Triple it.
            test.slow();
            const username = studentOne.username;
            await navigationBar.openCourseManagement();
            await courseManagement.openCourse(course.id!);
            await courseManagement.addStudentToCourse(studentOne);
            await expect(courseManagement.getRegisteredStudents().filter({ hasText: username })).toBeVisible();
            await navigationBar.openCourseManagement();
            await courseManagement.openCourse(course.id!);
            await expect(courseManagement.getNumberOfStudents().filter({ hasText: '1' })).toBeVisible();

            await navigationBar.openCourseManagement();
            await courseManagement.openStudentOverviewOfCourse(course.id!);
            await courseManagement.removeFirstUser();
            await expect(courseManagement.getRegisteredStudents().filter({ hasText: username })).toBeHidden();
            await navigationBar.openCourseManagement();
            await courseManagement.openCourse(course.id!);
            await expect(courseManagement.getNumberOfStudents().filter({ hasText: '0' })).toBeVisible();
        });

        test.afterEach(async ({ courseManagementAPIRequests }) => {
            await courseManagementAPIRequests.deleteCourse(course, admin);
        });
    });

    test.describe('Course creation', () => {
        let course: Course | undefined;

        test.beforeEach('Set course title and shortname', async ({ login }) => {
            await login(admin, '/');
            // Reset the closure variable so the previous test's (already-deleted) course
            // reference isn't carried into this test's afterEach. Without this reset the
            // afterEach below would call deleteCourse on a stale id and the server would
            // return 404, which historically (before the null-check added in #11885) showed
            // up as a server-side ConstraintViolationException — the source of the long-lived
            // "ConstraintViolationError" comment + 5 s retry workaround in deleteCourse.
            course = undefined;
            const uid = generateUUID();
            courseData.title = 'Course ' + uid;
            courseData.shortName = 'playwright' + uid;
        });

        test('Creates a new course', async ({ page, navigationBar, courseManagement, courseCreation }) => {
            await navigationBar.openCourseManagement();
            await courseManagement.openCourseCreation();
            await courseCreation.setTitle(courseData.title);
            await courseCreation.setShortName(courseData.shortName);
            await courseCreation.setDescription(courseData.description);
            await courseCreation.setTestCourse(courseData.testCourse);
            await courseCreation.setStartDate(courseData.startDate);
            await courseCreation.setEndDate(courseData.endDate);
            await courseCreation.setSemester(courseData.semester);
            await courseCreation.setCourseMaxPoints(courseData.maxPoints);
            await courseCreation.setProgrammingLanguage(courseData.programmingLanguage);
            await courseCreation.setEnableComplaints(courseData.enableComplaints);
            await courseCreation.setMaxComplaints(courseData.maxComplaints);
            await courseCreation.setMaxTeamComplaints(courseData.maxTeamComplaints);
            await courseCreation.setMaxComplaintsTimeDays(courseData.maxComplaintTimeDays);
            await courseCreation.setEnableMoreFeedback(courseData.enableMoreFeedback);
            await courseCreation.setMaxRequestMoreFeedbackTimeDays(courseData.maxRequestMoreFeedbackTimeDays);

            // The creation endpoint answers with the new course's id only, so the stored course is read back to
            // check that every field the form submitted arrived. The id is recorded first, so that a failing
            // assertion below still leaves the course for afterEach to delete.
            const { id: courseId } = await courseCreation.submit();
            course = new Course();
            course.id = courseId;

            const storedCourse = await (await page.request.get(`api/course/courses/${courseId}`)).json();

            expect(storedCourse.title).toBe(courseData.title);
            expect(storedCourse.shortName).toBe(courseData.shortName);
            expect(storedCourse.description).toBe(courseData.description);
            expect(storedCourse.testCourse).toBe(courseData.testCourse);
            expect(trimDate(storedCourse.startDate)).toBe(trimDate(dayjsToString(courseData.startDate)));
            expect(trimDate(storedCourse.endDate)).toBe(trimDate(dayjsToString(courseData.endDate)));
            expect(storedCourse.semester).toBe(courseData.semester);
            expect(storedCourse.maxPoints).toBe(courseData.maxPoints);
            expect(storedCourse.defaultProgrammingLanguage).toBe(courseData.programmingLanguage);
            expect(storedCourse.complaintsEnabled).toBe(courseData.enableComplaints);
            expect(storedCourse.maxComplaints).toBe(courseData.maxComplaints);
            expect(storedCourse.maxTeamComplaints).toBe(courseData.maxTeamComplaints);
            expect(storedCourse.maxComplaintTimeDays).toBe(courseData.maxComplaintTimeDays);
            expect(storedCourse.requestMoreFeedbackEnabled).toBe(courseData.enableMoreFeedback);

            // After a successful create the app auto-navigates to the new course's detail page, but
            // under heavy multi-node load that client-side navigation occasionally does not fire (the
            // create form stays mounted). Wait for the expected URL and fall back to an explicit goto
            // so the detail assertions below test the rendered course instead of racing the navigation.
            const courseDetailUrl = new RegExp(`/course-management/${courseId}(/|$)`);
            const navigated = await page
                .waitForURL(courseDetailUrl, { timeout: 15_000 })
                .then(() => true)
                .catch(() => false);
            if (!navigated) {
                await page.goto(`/course-management/${courseId}`);
                await page.waitForURL(courseDetailUrl, { timeout: 30_000 });
            }

            await expect(courseManagement.getCourseSidebarTitle().filter({ hasText: courseData.title })).toBeVisible();
            await expect(courseManagement.getCourseTitle().filter({ hasText: courseData.title })).toBeVisible();
            await expect(courseManagement.getCourseShortName().filter({ hasText: courseData.shortName })).toBeVisible();
            await expect(courseManagement.getNumberOfStudents().filter({ hasText: '0' })).toBeVisible();
            await expect(courseManagement.getNumberOfTutors().filter({ hasText: '0' })).toBeVisible();
            await expect(courseManagement.getNumberOfEditors().filter({ hasText: '0' })).toBeVisible();
            await expect(courseManagement.getNumberOfInstructors().filter({ hasText: '0' })).toBeVisible();
            await expect(courseManagement.getCourseStartDate().filter({ hasText: courseData.startDate.format(dateFormat) })).toBeVisible();
            await expect(courseManagement.getCourseEndDate().filter({ hasText: courseData.endDate.format(dateFormat) })).toBeVisible();
            await expect(courseManagement.getCourseSemester().filter({ hasText: courseData.semester })).toBeVisible();
            await expect(courseManagement.getCourseProgrammingLanguage().filter({ hasText: courseData.programmingLanguage })).toBeVisible();
            await expect(courseManagement.getCourseTestCourse().locator(convertBooleanToCheckIconClass(courseData.testCourse))).toBeVisible();
            await expect(courseManagement.getCourseMaxComplaints().filter({ hasText: courseData.maxComplaints.toString() })).toBeVisible();
            await expect(courseManagement.getCourseMaxTeamComplaints().filter({ hasText: courseData.maxTeamComplaints.toString() })).toBeVisible();
            await expect(courseManagement.getMaxComplaintTimeDays().filter({ hasText: courseData.maxComplaintTimeDays.toString() })).toBeVisible();
            await expect(courseManagement.getMaxRequestMoreFeedbackTimeDays().filter({ hasText: courseData.maxRequestMoreFeedbackTimeDays.toString() })).toBeVisible();
        });

        test.afterEach(async ({ courseManagementAPIRequests }) => {
            await courseManagementAPIRequests.deleteCourse(course, admin);
        });
    });

    test.describe('Course edit', () => {
        let course: Course;

        test.beforeEach(async ({ login, courseManagementAPIRequests }) => {
            await login(admin, '/');
            const uid = generateUUID();
            courseData.title = 'Course ' + uid;
            courseData.shortName = 'playwright' + uid;
            course = await courseManagementAPIRequests.createCourse({ courseName: courseData.title, courseShortName: courseData.shortName });
        });

        test('Edits a existing course', async ({ navigationBar, courseManagement, courseCreation }) => {
            const uid = generateUUID();
            editedCourseData.title = 'Course ' + uid;

            await navigationBar.openCourseManagement();
            await courseManagement.openCourse(course.id!);
            await courseManagement.openCourseSettings();

            await courseCreation.setTitle(editedCourseData.title);
            await courseCreation.setTestCourse(editedCourseData.testCourse);

            course = await courseCreation.update();
            expect(course.title).toBe(editedCourseData.title);
            expect(course.shortName).toBe(courseData.shortName);
            expect(course.testCourse).toBe(editedCourseData.testCourse);

            await expect(courseManagement.getCourseSidebarTitle().filter({ hasText: editedCourseData.title })).toBeVisible();
            await expect(courseManagement.getCourseTitle().filter({ hasText: editedCourseData.title })).toBeVisible();
            await expect(courseManagement.getCourseShortName().filter({ hasText: courseData.shortName })).toBeVisible();
            await expect(courseManagement.getCourseTestCourse().locator(convertBooleanToCheckIconClass(editedCourseData.testCourse))).toBeVisible();
        });

        test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
            await courseManagementAPIRequests.deleteCourse(course, admin);
        });
    });

    test.describe('Course deletion', () => {
        let course: Course;

        test.beforeEach(async ({ login, courseManagementAPIRequests }) => {
            await login(admin, '/');
            course = await courseManagementAPIRequests.createCourse();
        });

        test('Deletes an existing course', async ({ navigationBar, courseManagement }) => {
            await navigationBar.openCourseManagement();
            await courseManagement.openCourse(course.id!);
            await courseManagement.openCourseSettings();
            await courseManagement.deleteCourse(course);
            await expect(courseManagement.getCourse(course.id!)).toBeHidden();
        });

        test('Delete summary shows correct values', async ({
            page,
            navigationBar,
            courseManagement,
            courseManagementAPIRequests,
            exerciseAPIRequests,
            login,
            courseMessages,
            communicationAPIRequests,
        }) => {
            // Course delete with summary spawns ~15 API requests to populate the course +
            // a slow DELETE on a course that has exercises/exam/messages attached, then
            // re-loads the consolidated course dashboard. Even the
            // tripled @slow budget (180s) routinely overruns under heavy multi-node load.
            test.setTimeout(360_000);
            // Use API calls instead of UI navigation for faster user creation
            await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
            await courseManagementAPIRequests.addStudentToCourse(course, studentTwo);
            await courseManagementAPIRequests.addStudentToCourse(course, studentThree);
            await courseManagementAPIRequests.addTutorToCourse(course, tutor);
            await courseManagementAPIRequests.addInstructorToCourse(course, instructor);

            await exerciseAPIRequests.createProgrammingExercise({ course, programmingLanguage: ProgrammingLanguage.C });

            await exerciseAPIRequests.createModelingExercise({ course });
            await exerciseAPIRequests.createModelingExercise({ course });
            await exerciseAPIRequests.createModelingExercise({ course });

            await exerciseAPIRequests.createQuizExercise({ body: { course }, quizQuestions: [multipleChoiceQuizTemplate], title: 'Course Exercise Quiz 1' });
            await exerciseAPIRequests.createQuizExercise({ body: { course }, quizQuestions: [multipleChoiceQuizTemplate], title: 'Course Exercise Quiz 2' });

            await exerciseAPIRequests.createTextExercise({ course });

            await exerciseAPIRequests.createFileUploadExercise({ course });
            await exerciseAPIRequests.createFileUploadExercise({ course });

            await courseManagementAPIRequests.createLecture(course);
            await courseManagementAPIRequests.createLecture(course);

            const examAPIRequests = new ExamAPIRequests(page);
            await examAPIRequests.createExam({ course, title: 'Exam 1 - ' + generateUUID() });
            await examAPIRequests.createExam({ course, title: 'Exam 2 - ' + generateUUID() });
            await examAPIRequests.createExam({ course, title: 'Exam 3 - ' + generateUUID() });

            const channel = await courseMessages.setupCommunicationChannel(login, admin, course, communicationAPIRequests);
            const messageText = 'Test Message';
            await courseMessages.sendMessageInChannel(login, admin, course.id!, channel.id!, messageText + ' 1');
            await courseMessages.sendMessageInChannel(login, admin, course.id!, channel.id!, messageText + ' 2');

            const expectedCourseSummaryValues: CourseSummary = {
                isTestCourse: true,
                students: 3,
                tutors: 1,
                editors: 0,
                instructors: 1,
                exams: 3,
                lectures: 2,
                programingExercises: 1,
                modelingExercises: 3,
                quizExercises: 2,
                textExercises: 1,
                fileUploadExercises: 2,
                communicationPosts: 2,
            };

            await navigationBar.openCourseManagement();
            await courseManagement.openCourse(course.id!);
            await courseManagement.openCourseSettings();
            await courseManagement.deleteCourse(course, expectedCourseSummaryValues);
        });
    });

    test.describe('Course icon deletion', () => {
        test.describe('Course within icon', () => {
            let course: Course;

            test.beforeEach('Creates course with icon', async ({ login, courseManagementAPIRequests }) => {
                await login(admin, '/');
                const courseIcon = await Fixtures.get('course/icon.png', 'base64');
                const iconBlob = base64StringToBlob(courseIcon!);
                course = await courseManagementAPIRequests.createCourse({ iconFileName: 'icon.png', iconFile: iconBlob });
            });

            test('Deletes an existing course icon', async ({ navigationBar, courseManagement }) => {
                await navigationBar.openCourseManagement();
                await courseManagement.openCourse(course.id!);
                await courseManagement.openCourseSettings();
                await courseManagement.removeIconFromCourse();
                await courseManagement.updateCourse(course);
                await courseManagement.openCourseSettings();
                await courseManagement.checkCourseHasNoIcon();
            });

            test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
                await courseManagementAPIRequests.deleteCourse(course, admin);
            });
        });

        test.describe('Course without icon', () => {
            let course: Course;

            test.beforeEach('Creates course without icon', async ({ login, courseManagementAPIRequests }) => {
                await login(admin, '/');
                course = await courseManagementAPIRequests.createCourse();
            });

            test('Deletes not existing course icon', async ({ navigationBar, courseManagement }) => {
                await navigationBar.openCourseManagement();
                await courseManagement.openCourse(course.id!);
                await courseManagement.openCourseSettings();
                await courseManagement.checkCourseHasNoIcon();
            });

            test.afterEach('Delete courses', async ({ courseManagementAPIRequests }) => {
                await courseManagementAPIRequests.deleteCourse(course, admin);
            });
        });
    });
});
