import { Course } from '../../../../main/webapp/app/entities/course.model';
import { courseCommunication, courseManagementRequest, navigationBar } from '../../support/artemis';
import { CourseWideContext } from '../../support/constants';
import { convertCourseAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import { admin, instructor, studentOne, studentTwo } from '../../support/users';
import { generateUUID } from '../../support/utils';

// Common primitives
let courseName: string;
let courseShortName: string;

describe('Course communication', () => {
    let course: Course;
    let courseId: number;

    before('Create course', () => {
        cy.login(admin);
        const uid = generateUUID();
        courseName = 'Cypress course' + uid;
        courseShortName = 'cypress' + uid;
        courseManagementRequest.createCourse(false, courseName, courseShortName).then((response) => {
            course = convertCourseAfterMultiPart(response);
            courseId = course.id!;
            courseManagementRequest.addInstructorToCourse(course, instructor);
            courseManagementRequest.addStudentToCourse(course, studentOne);
            courseManagementRequest.addStudentToCourse(course, studentTwo);
        });
    });

    describe('Course overview communication', () => {
        it('student should be able to create post', () => {
            cy.login(studentOne, `/courses/${course.id}/discussion`);
            courseCommunication.newPost();
            courseCommunication.selectContext(CourseWideContext.ORGANIZATION);
            courseCommunication.setTitle('Cypress Test Post');
            cy.fixture('loremIpsum.txt').then((text) => {
                courseCommunication.setContent(text);
            });
            courseCommunication.save();
        });

        it('student should not be able to create announcement post', () => {
            cy.login(studentOne, `/courses/${course.id}/discussion`);
            courseCommunication.newPost();
            courseCommunication.getContextSelector().eq(0).should('not.contain', CourseWideContext.ANNOUNCEMENT);
        });

        it('instructor should be able to create announcement post', () => {
            cy.login(instructor, `/courses/${course.id}/discussion`);
            courseCommunication.newPost();
            courseCommunication.selectContext(CourseWideContext.ANNOUNCEMENT);
            courseCommunication.setTitle('Cypress Test Post');
            cy.fixture('loremIpsum.txt').then((text) => {
                courseCommunication.setContent(text);
                courseCommunication.save();
                cy.login(studentTwo, `/courses/${course.id}/discussion`);
                navigationBar.openNotificationPanel();
                navigationBar.getNotifications().first().find('.notification-title').contains('New announcement');
                navigationBar.getNotifications().first().find('.notification-text').contains(`Course "${course.title}" got a new announcement.`);
            });
        });

        it('other students should be able to see post', () => {
            const title = 'My Test Post';
            const content = 'Test Post Content';
            const context = CourseWideContext.TECH_SUPPORT;
            cy.login(studentOne, `/courses/${course.id}/discussion`);
            courseManagementRequest.createCoursePost(course, title, content, context).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${course.id}/discussion`);
                cy.reload();
                courseCommunication.checkSinglePost(post.id, title, content, context);
            });
        });

        it('other students should be able to search for post', () => {
            const title = 'My Search Test Post';
            const content = 'Test Search Post Content';
            const context = CourseWideContext.TECH_SUPPORT;
            cy.login(studentOne, `/courses/${course.id}/discussion`);
            courseManagementRequest.createCoursePost(course, title, content, context).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${course.id}/discussion`);
                cy.reload();
                courseCommunication.searchForPost(title);
                courseCommunication.checkSinglePost(post.id, title, content, context);
            });
        });

        it('other students should be able to filter for post', () => {
            const title = 'My Filter Test Post';
            const content = 'Test Filter Post Content';
            const context = CourseWideContext.RANDOM;
            cy.login(studentOne, `/courses/${course.id}/discussion`);

            courseManagementRequest.createCoursePost(course, title, content, context).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${course.id}/discussion`);
                cy.reload();
                courseCommunication.filterByContext(context);
                courseCommunication.checkSinglePost(post.id, title, content, context);
            });
        });

        it('other students should be able to reply to post', () => {
            const title = 'My Reply Test Post';
            const content = 'Test Reply Post Content';
            const context = CourseWideContext.RANDOM;
            cy.login(studentOne, `/courses/${course.id}/discussion`);
            courseManagementRequest.createCoursePost(course, title, content, context).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${course.id}/discussion`);
                cy.reload();
                courseCommunication.reply(post.id, 'My Test reply');
            });
        });

        it('students should be able to edit their post', () => {
            const title = 'My Edit Test Post';
            const content = 'Test Edit Post Content';
            const context = CourseWideContext.RANDOM;
            const newTitle = 'My Edited Test Post';
            const newContent = 'Test Edited Post Content';
            cy.login(studentOne, `/courses/${course.id}/discussion`);
            courseManagementRequest.createCoursePost(course, title, content, context).then((response) => {
                const post = response.body;
                cy.reload();
                courseCommunication.editPost(post.id, newTitle, newContent);
                courseCommunication.checkSinglePost(post.id, newTitle, newContent, context);
            });
        });

        it('students should be able to delete their post', () => {
            const title = 'My Delete Test Post';
            const content = 'Test Delete Post Content';
            const context = CourseWideContext.RANDOM;
            cy.login(studentOne, `/courses/${course.id}/discussion`);
            courseManagementRequest.createCoursePost(course, title, content, context).then((response) => {
                const post = response.body;
                cy.reload();
                courseCommunication.deletePost(post.id);
            });
        });
    });

    after('Delete Course', () => {
        cy.login(admin);
        if (courseId) {
            courseManagementRequest.deleteCourse(courseId).its('status').should('eq', 200);
        }
    });
});
