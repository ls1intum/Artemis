import { test } from '../../support/fixtures';
import dayjs from 'dayjs';
import { Course } from 'app/core/course/shared/entities/course.model';
import { admin, instructor, PlaywrightUserManagement, studentOne, studentThree, studentTwo, tutor, UserRole } from '../../support/users';
import { base64StringToBlob, convertBooleanToCheckIconClass, dayjsToString, generateUUID, trimDate } from '../../support/utils';
import { expect } from '@playwright/test';
import { Fixtures } from '../../fixtures/fixtures';

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

export interface CourseUserCounts {
    students: number;
    tutors: number;
    editors: number;
    instructors: number;
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

        test('Delete summary shows correct values', async ({ page, navigationBar, courseManagement }) => {
            await PlaywrightUserManagement.createUserInCourse(course.id!, studentOne, UserRole.Student, navigationBar, courseManagement);
            await PlaywrightUserManagement.createUserInCourse(course.id!, studentTwo, UserRole.Student, navigationBar, courseManagement);
            await PlaywrightUserManagement.createUserInCourse(course.id!, studentThree, UserRole.Student, navigationBar, courseManagement);
            await PlaywrightUserManagement.createUserInCourse(course.id!, tutor, UserRole.Tutor, navigationBar, courseManagement);
            await PlaywrightUserManagement.createUserInCourse(course.id!, instructor, UserRole.Instructor, navigationBar, courseManagement);
            const courseUserCounts: CourseUserCounts = {
                students: 3,
                tutors: 1,
                editors: 0,
                instructors: 1,
            };

            await navigationBar.openCourseManagement();
            await courseManagement.openCourse(course.id!);
            await courseManagement.openCourseSettings();
            await courseManagement.deleteCourse(course, courseUserCounts);
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
