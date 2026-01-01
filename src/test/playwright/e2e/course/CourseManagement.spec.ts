import { test } from '../../support/fixtures';
import dayjs from 'dayjs';
import { Course } from 'app/core/course/shared/entities/course.model';
import { admin, instructor, PlaywrightUserManagement, studentOne, studentThree, studentTwo, tutor, UserRole } from '../../support/users';
import { base64StringToBlob, convertBooleanToCheckIconClass, dayjsToString, generateUUID, trimDate } from '../../support/utils';
import { expect } from '@playwright/test';
import { Fixtures } from '../../fixtures/fixtures';
import multipleChoiceQuizTemplate from '../../fixtures/exercise/quiz/multiple_choice/template.json';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';

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
    customizeGroupNames: false,
    studentGroupName: process.env.STUDENT_GROUP_NAME ?? '',
    tutorGroupName: process.env.TUTOR_GROUP_NAME ?? '',
    editorGroupName: process.env.EDITOR_GROUP_NAME ?? '',
    instructorGroupName: process.env.INSTRUCTOR_GROUP_NAME ?? '',
    enableComplaints: true,
    enableFaqs: true,
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

const allowGroupCustomization = process.env.ALLOW_GROUP_CUSTOMIZATION;
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
        let course: Course;
        let course2: Course;

        test.beforeEach('Set course title and shortname', async ({ login }) => {
            await login(admin, '/');
            const uid = generateUUID();
            courseData.title = 'Course ' + uid;
            courseData.shortName = 'playwright' + uid;
        });

        test('Creates a new course', async ({ navigationBar, courseManagement, courseCreation }) => {
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
            await courseCreation.setEnableFaq(courseData.enableFaqs);
            await courseCreation.setMaxComplaints(courseData.maxComplaints);
            await courseCreation.setMaxTeamComplaints(courseData.maxTeamComplaints);
            await courseCreation.setMaxComplaintsTimeDays(courseData.maxComplaintTimeDays);
            await courseCreation.setEnableMoreFeedback(courseData.enableMoreFeedback);
            await courseCreation.setMaxRequestMoreFeedbackTimeDays(courseData.maxRequestMoreFeedbackTimeDays);
            await courseCreation.setCustomizeGroupNames(courseData.customizeGroupNames);

            const courseBody = await courseCreation.submit();
            course = courseBody;

            expect(courseBody.title).toBe(courseData.title);
            expect(courseBody.shortName).toBe(courseData.shortName);
            expect(courseBody.description).toBe(courseData.description);
            expect(courseBody.testCourse).toBe(courseData.testCourse);
            expect(trimDate(courseBody.startDate)).toBe(trimDate(dayjsToString(courseData.startDate)));
            expect(trimDate(courseBody.endDate)).toBe(trimDate(dayjsToString(courseData.endDate)));
            expect(courseBody.semester).toBe(courseData.semester);
            expect(courseBody.maxPoints).toBe(courseData.maxPoints);
            expect(courseBody.defaultProgrammingLanguage).toBe(courseData.programmingLanguage);
            expect(courseBody.complaintsEnabled).toBe(courseData.enableComplaints);
            expect(courseBody.faqEnabled).toBe(courseData.enableFaqs);
            expect(courseBody.maxComplaints).toBe(courseData.maxComplaints);
            expect(courseBody.maxTeamComplaints).toBe(courseData.maxTeamComplaints);
            expect(courseBody.maxComplaintTimeDays).toBe(courseData.maxComplaintTimeDays);
            expect(courseBody.requestMoreFeedbackEnabled).toBe(courseData.enableMoreFeedback);
            expect(courseBody.studentGroupName).toBe(`artemis-${courseData.shortName}-students`);
            expect(courseBody.editorGroupName).toBe(`artemis-${courseData.shortName}-editors`);
            expect(courseBody.instructorGroupName).toBe(`artemis-${courseData.shortName}-instructors`);
            expect(courseBody.teachingAssistantGroupName).toBe(`artemis-${courseData.shortName}-tutors`);

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

        if (allowGroupCustomization) {
            test('Creates a new course with custom groups', async ({ navigationBar, courseManagement, courseCreation }) => {
                await navigationBar.openCourseManagement();
                await courseManagement.openCourseCreation();
                await courseCreation.setTitle(courseData.title);
                await courseCreation.setShortName(courseData.shortName);
                await courseCreation.setTestCourse(courseData.testCourse);
                await courseCreation.setCustomizeGroupNames(true);
                await courseCreation.setStudentGroup(courseData.studentGroupName);
                await courseCreation.setTutorGroup(courseData.tutorGroupName);
                await courseCreation.setEditorGroup(courseData.editorGroupName);
                await courseCreation.setInstructorGroup(courseData.instructorGroupName);

                const courseBody = await courseCreation.submit();
                course2 = courseBody;

                expect(courseBody.title).toBe(courseData.title);
                expect(courseBody.shortName).toBe(courseData.shortName);
                expect(courseBody.testCourse).toBe(courseData.testCourse);
                expect(courseBody.studentGroupName).toBe(courseData.studentGroupName);
                expect(courseBody.teachingAssistantGroupName).toBe(courseData.tutorGroupName);
                expect(courseBody.editorGroupName).toBe(courseData.editorGroupName);
                expect(courseBody.instructorGroupName).toBe(courseData.instructorGroupName);

                await expect(courseManagement.getCourseSidebarTitle().filter({ hasText: courseData.title })).toBeVisible();
                await expect(courseManagement.getCourseTitle().filter({ hasText: courseData.title })).toBeVisible();
                await expect(courseManagement.getCourseShortName().filter({ hasText: courseData.shortName })).toBeVisible();
                await expect(courseManagement.getCourseTestCourse().locator(convertBooleanToCheckIconClass(courseData.testCourse))).toBeVisible();
            });
        }

        test.afterEach(async ({ courseManagementAPIRequests }) => {
            await courseManagementAPIRequests.deleteCourse(course, admin);
            await courseManagementAPIRequests.deleteCourse(course2, admin);
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
            // Use API calls instead of UI navigation for faster user creation
            await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
            await courseManagementAPIRequests.addStudentToCourse(course, studentTwo);
            await courseManagementAPIRequests.addStudentToCourse(course, studentThree);
            await courseManagementAPIRequests.addTutorToCourse(course, tutor);
            await courseManagementAPIRequests.addInstructorToCourse(course, instructor);

            await exerciseAPIRequests.createProgrammingExercise({ course });

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
            await courseMessages.sendMessageInChannel(login, admin, course.id!, channel.id, messageText + ' 1');
            await courseMessages.sendMessageInChannel(login, admin, course.id!, channel.id, messageText + ' 2');

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
