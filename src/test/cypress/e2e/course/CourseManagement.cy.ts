import { Interception } from 'cypress/types/net-stubbing';
import { convertModelAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import { courseCreation, courseManagement, courseManagementRequest, navigationBar } from '../../support/artemis';
import { convertBooleanToYesNo, dayjsToString, generateUUID, trimDate } from '../../support/utils';
import { Course } from 'app/entities/course.model';
import day from 'dayjs/esm';
import { admin, studentOne } from '../../support/users';

// Common primitives
const courseData = {
    title: '',
    shortName: '',
    description: 'Lore Impsum',
    startDate: day(),
    endDate: day().add(1, 'day'),
    testCourse: true,
    semester: 'SS23',
    maxPoints: 40,
    programmingLanguage: 'JAVA',
    customizeGroupNames: false,
    studentGroupName: Cypress.env('studentGroupName'),
    tutorGroupName: Cypress.env('tutorGroupName'),
    editorGroupName: Cypress.env('editorGroupName'),
    instructorGroupName: Cypress.env('instructorGroupName'),
    enableComplaints: true,
    maxComplaints: 5,
    maxTeamComplaints: 3,
    maxComplaintTimeDays: 6,
    enableMoreFeedback: true,
    maxRequestMoreFeedbackTimeDays: 4,
    onlineCourse: true,
    presentationScoreEnabled: true,
    presentationScore: 10,
};

const editedCourseData = {
    title: '',
    testCourse: false,
};

const allowGroupCustomization: boolean = Cypress.env('allowGroupCustomization');
const dateFormat = 'MMM D, YYYY HH:mm';

describe('Course management', () => {
    describe('Manual student selection', () => {
        let course: Course;

        beforeEach('Create course', () => {
            cy.login(admin, '/');
            const uid = generateUUID();
            courseData.title = 'Course ' + uid;
            courseData.shortName = 'cypress' + uid;
            courseManagementRequest.createCourse(false, courseData.title, courseData.shortName).then((response) => {
                course = convertModelAfterMultiPart(response);
            });
        });

        it('Adds a student manually to the course', () => {
            const username = studentOne.username;
            navigationBar.openCourseManagement();
            courseManagement.openCourse(course.id!);
            courseManagement.addStudentToCourse(studentOne);
            courseManagement.getRegisteredStudents().contains(username).should('be.visible');
            navigationBar.openCourseManagement();
            courseManagement.openCourse(course.id!);
            courseManagement.getCourseStudentGroupName().contains(`artemis-${course.shortName}-students (1)`);
        });

        it('Removes a student manually from the course', () => {
            const username = studentOne.username;
            courseManagementRequest.addStudentToCourse(course, studentOne);
            navigationBar.openCourseManagement();
            courseManagement.openStudentOverviewOfCourse(course.id!);
            courseManagement.getRegisteredStudents().contains(username).should('be.visible');
            courseManagement.removeFirstUser();
            courseManagement.getRegisteredStudents().contains(username).should('not.exist');
            navigationBar.openCourseManagement();
            courseManagement.openCourse(course.id!);
            courseManagement.getCourseStudentGroupName().contains(`artemis-${course.shortName}-students (0)`);
        });

        afterEach('Delete course', () => {
            courseManagementRequest.deleteCourse(course, admin);
        });
    });

    describe('Course creation', () => {
        let course: Course;
        let course2: Course;

        beforeEach('Set course title and shortname', () => {
            cy.login(admin, '/');
            const uid = generateUUID();
            courseData.title = 'Course ' + uid;
            courseData.shortName = 'cypress' + uid;
        });

        it('Creates a new course', () => {
            navigationBar.openCourseManagement();
            courseManagement.openCourseCreation();
            courseCreation.setTitle(courseData.title);
            courseCreation.setShortName(courseData.shortName);
            courseCreation.setDescription(courseData.description);
            courseCreation.setTestCourse(courseData.testCourse);
            courseCreation.setStartDate(courseData.startDate);
            courseCreation.setEndDate(courseData.endDate);
            courseCreation.setSemester(courseData.semester);
            courseCreation.setCourseMaxPoints(courseData.maxPoints);
            courseCreation.setProgrammingLanguage(courseData.programmingLanguage);
            courseCreation.setEnableComplaints(courseData.enableComplaints);
            courseCreation.setMaxComplaints(courseData.maxComplaints);
            courseCreation.setMaxTeamComplaints(courseData.maxTeamComplaints);
            courseCreation.setMaxComplaintsTimeDays(courseData.maxComplaintTimeDays);
            courseCreation.setEnableMoreFeedback(courseData.enableMoreFeedback);
            courseCreation.setMaxRequestMoreFeedbackTimeDays(courseData.maxRequestMoreFeedbackTimeDays);
            courseCreation.setOnlineCourse(courseData.onlineCourse);
            courseCreation.setCustomizeGroupNames(courseData.customizeGroupNames);
            courseCreation.submit().then((request: Interception) => {
                const courseBody = request.response!.body;
                course = courseBody;
                expect(courseBody.title).to.eq(courseData.title);
                expect(courseBody.shortName).to.eq(courseData.shortName);
                expect(courseBody.description).to.eq(courseData.description);
                expect(courseBody.testCourse).to.eq(courseData.testCourse);
                expect(trimDate(courseBody.startDate)).to.eq(trimDate(dayjsToString(courseData.startDate)));
                expect(trimDate(courseBody.endDate)).to.eq(trimDate(dayjsToString(courseData.endDate)));
                expect(courseBody.semester).to.eq(courseData.semester);
                expect(courseBody.maxPoints).to.eq(courseData.maxPoints);
                expect(courseBody.defaultProgrammingLanguage).to.eq(courseData.programmingLanguage);
                expect(courseBody.complaintsEnabled).to.eq(courseData.enableComplaints);
                expect(courseBody.maxComplaints).to.eq(courseData.maxComplaints);
                expect(courseBody.maxTeamComplaints).to.eq(courseData.maxTeamComplaints);
                expect(courseBody.maxComplaintTimeDays).to.eq(courseData.maxComplaintTimeDays);
                expect(courseBody.requestMoreFeedbackEnabled).to.eq(courseData.enableMoreFeedback);
                expect(courseBody.onlineCourse).to.eq(courseData.onlineCourse);
                expect(courseBody.studentGroupName).to.eq(`artemis-${courseData.shortName}-students`);
                expect(courseBody.editorGroupName).to.eq(`artemis-${courseData.shortName}-editors`);
                expect(courseBody.instructorGroupName).to.eq(`artemis-${courseData.shortName}-instructors`);
                expect(courseBody.teachingAssistantGroupName).to.eq(`artemis-${courseData.shortName}-tutors`);
            });
            courseManagement.getCourseHeaderTitle().contains(courseData.title).should('be.visible');
            courseManagement.getCourseHeaderDescription().contains(courseData.description);
            courseManagement.getCourseTitle().contains(courseData.title);
            courseManagement.getCourseShortName().contains(courseData.shortName);
            courseManagement.getCourseStudentGroupName().contains(`artemis-${courseData.shortName}-students (0)`);
            courseManagement.getCourseTutorGroupName().contains(`artemis-${courseData.shortName}-tutors (0)`);
            courseManagement.getCourseEditorGroupName().contains(`artemis-${courseData.shortName}-editors (0)`);
            courseManagement.getCourseInstructorGroupName().contains(`artemis-${courseData.shortName}-instructors (0)`);
            courseManagement.getCourseStartDate().contains(courseData.startDate.format(dateFormat));
            courseManagement.getCourseEndDate().contains(courseData.endDate.format(dateFormat));
            courseManagement.getCourseSemester().contains(courseData.semester);
            courseManagement.getCourseProgrammingLanguage().contains(courseData.programmingLanguage);
            courseManagement.getCourseTestCourse().contains(convertBooleanToYesNo(courseData.testCourse));
            courseManagement.getCourseOnlineCourse().contains(convertBooleanToYesNo(courseData.onlineCourse));
            courseManagement.getCourseMaxComplaints().contains(courseData.maxComplaints);
            courseManagement.getCourseMaxTeamComplaints().contains(courseData.maxTeamComplaints);
            courseManagement.getMaxComplaintTimeDays().contains(courseData.maxComplaintTimeDays);
            courseManagement.getMaxRequestMoreFeedbackTimeDays().contains(courseData.maxRequestMoreFeedbackTimeDays);
        });

        if (allowGroupCustomization) {
            it('Creates a new course with custom groups', () => {
                navigationBar.openCourseManagement();
                courseManagement.openCourseCreation();
                courseCreation.setTitle(courseData.title);
                courseCreation.setShortName(courseData.shortName);
                courseCreation.setTestCourse(courseData.testCourse);
                courseCreation.setCustomizeGroupNames(true);
                courseCreation.setStudentGroup(courseData.studentGroupName);
                courseCreation.setTutorGroup(courseData.tutorGroupName);
                courseCreation.setEditorGroup(courseData.editorGroupName);
                courseCreation.setInstructorGroup(courseData.instructorGroupName);
                courseCreation.submit().then((request: Interception) => {
                    const courseBody = request.response!.body;
                    course2 = courseBody;
                    expect(courseBody.title).to.eq(courseData.title);
                    expect(courseBody.shortName).to.eq(courseData.shortName);
                    expect(courseBody.testCourse).to.eq(courseData.testCourse);
                    expect(courseBody.studentGroupName).to.eq(courseData.studentGroupName);
                    expect(courseBody.teachingAssistantGroupName).to.eq(courseData.tutorGroupName);
                    expect(courseBody.editorGroupName).to.eq(courseData.editorGroupName);
                    expect(courseBody.instructorGroupName).to.eq(courseData.instructorGroupName);
                });
                courseManagement.getCourseHeaderTitle().contains(courseData.title).should('be.visible');
                courseManagement.getCourseTitle().contains(courseData.title);
                courseManagement.getCourseShortName().contains(courseData.shortName);
                courseManagement.getCourseTestCourse().contains(convertBooleanToYesNo(courseData.testCourse));
                courseManagement.getCourseStudentGroupName().contains(courseData.studentGroupName);
                courseManagement.getCourseTutorGroupName().contains(courseData.tutorGroupName);
                courseManagement.getCourseEditorGroupName().contains(courseData.editorGroupName);
                courseManagement.getCourseInstructorGroupName().contains(courseData.instructorGroupName);
            });
        }

        after('Delete courses', () => {
            courseManagementRequest.deleteCourse(course, admin);
            courseManagementRequest.deleteCourse(course2, admin);
        });
    });

    describe('Course edit', () => {
        let course: Course;

        before('Create course', () => {
            cy.login(admin, '/');
            const uid = generateUUID();
            courseData.title = 'Course ' + uid;
            courseData.shortName = 'cypress' + uid;
            courseManagementRequest.createCourse(false, courseData.title, courseData.shortName).then((response) => {
                course = convertModelAfterMultiPart(response);
            });
        });

        it('Edits a existing course', () => {
            const uid = generateUUID();
            editedCourseData.title = 'Course ' + uid;

            navigationBar.openCourseManagement();
            courseManagement.openCourse(course.id!);
            courseManagement.openCourseEdit();

            courseCreation.setTitle(editedCourseData.title);
            courseCreation.setTestCourse(editedCourseData.testCourse);

            courseCreation.update().then((request: Interception) => {
                course = request.response!.body;
                expect(course.title).to.eq(editedCourseData.title);
                expect(course.shortName).to.eq(courseData.shortName);
                expect(course.testCourse).to.eq(editedCourseData.testCourse);
            });
            courseManagement.getCourseHeaderTitle().contains(editedCourseData.title).should('be.visible');
            courseManagement.getCourseTitle().contains(editedCourseData.title);
            courseManagement.getCourseShortName().contains(courseData.shortName);
            courseManagement.getCourseTestCourse().contains(convertBooleanToYesNo(editedCourseData.testCourse));
        });

        after('Delete course', () => {
            courseManagementRequest.deleteCourse(course, admin);
        });
    });

    describe('Course deletion', () => {
        let course: Course;

        before('Create course', () => {
            cy.login(admin, '/');
            courseManagementRequest.createCourse().then((response) => {
                course = convertModelAfterMultiPart(response);
            });
        });

        it('Deletes an existing course', () => {
            navigationBar.openCourseManagement();
            courseManagement.openCourse(course.id!);
            courseManagement.deleteCourse(course);
            courseManagement.getCourse(course.id!).should('not.exist');
        });
    });

    describe('Course icon deletion', () => {
        describe('Course within icon', () => {
            let course: Course;

            before('Creates course with icon', () => {
                cy.login(admin, '/');
                cy.fixture('course/icon.png', 'base64')
                    .then(Cypress.Blob.base64StringToBlob)
                    .then((blob) => {
                        courseManagementRequest.createCourse(false, undefined, undefined, day().subtract(2, 'hours'), day().add(2, 'hours'), 'icon.png', blob).then((response) => {
                            course = convertModelAfterMultiPart(response);
                        });
                    });
            });

            it('Deletes an existing course icon', () => {
                navigationBar.openCourseManagement();
                courseManagement.openCourse(course.id!);
                courseManagement.clickEditCourse();
                courseManagement.removeIconFromCourse();
                courseManagement.updateCourse(course).then(() => {
                    courseManagement.clickEditCourse();
                    courseManagement.checkCourseHasNoIcon();
                });
            });

            after('Delete course', () => {
                courseManagementRequest.deleteCourse(course, admin);
            });
        });

        describe('Course without icon', () => {
            let course: Course;

            before('Creates course without icon', () => {
                cy.login(admin, '/');
                courseManagementRequest.createCourse().then((response) => {
                    course = convertModelAfterMultiPart(response);
                });
            });

            it('Deletes not existing course icon', () => {
                navigationBar.openCourseManagement();
                courseManagement.openCourse(course.id!);
                courseManagement.clickEditCourse();
                courseManagement.checkCourseHasNoIcon();
            });

            after('Delete courses', () => {
                courseManagementRequest.deleteCourse(course, admin);
            });
        });
    });
});
