import { TextExercise } from 'app/entities/text-exercise.model';
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

        it('instructor should be able pin a post', () => {
            const title = 'Pin Test Post';
            const content = 'Pin Post Content';
            const context = CourseWideContext.RANDOM;
            cy.login(studentOne, `/courses/${course.id}/discussion`);
            courseManagementRequest.createCoursePost(course, title, content, context).then((response) => {
                const post = response.body;
                cy.login(instructor, `/courses/${course.id}/discussion`);
                courseCommunication.pinPost(post.id);
                cy.reload();
                courseCommunication.checkSinglePostByPosition(0, title, content, context);
            });
        });

        it('student should not be able to create announcement post', () => {
            cy.login(studentOne, `/courses/${course.id}/discussion`);
            courseCommunication.newPost();
            courseCommunication.getContextSelector().eq(0).should('not.contain', CourseWideContext.ANNOUNCEMENT);
        });

        it('instructor should be able to create announcement post', () => {
            const title = 'Announcement Test Post';
            const content = 'Announcement Post Content';
            cy.login(instructor, `/courses/${course.id}/discussion`);
            courseCommunication.newPost();
            courseCommunication.selectContext(CourseWideContext.ANNOUNCEMENT);
            courseCommunication.setTitle(title);
            courseCommunication.setContent(content);
            courseCommunication.save();
            cy.login(studentTwo, `/courses/${course.id}/discussion`);
            navigationBar.openNotificationPanel();
            navigationBar.getNotifications().first().find('.notification-title').contains('New announcement');
            navigationBar
                .getNotifications()
                .first()
                .find('.notification-text')
                .contains((`The course "` + courseName + `" got a new announcement: "` + content + `"`).substring(0, 300 - 1) + '...');
        });

        it('instructor should be able to archive a post', () => {
            const title = 'Archive Test Post';
            const content = 'Archive Post Content';
            const context = CourseWideContext.RANDOM;
            cy.login(studentOne, `/courses/${course.id}/discussion`);
            courseManagementRequest.createCoursePost(course, title, content, context).then((response) => {
                const post = response.body;
                cy.login(instructor, `/courses/${course.id}/discussion`);
                courseCommunication.archivePost(post.id);
                cy.reload();
                courseCommunication.getSinglePost(post.id).should('not.exist');
            });
        });

        it('instructor should be able to select answer', () => {
            const title = 'Answer Test Post';
            const content = 'Answer Post Content';
            const context = CourseWideContext.TECH_SUPPORT;
            cy.login(studentOne, `/courses/${course.id}/discussion`);
            courseManagementRequest.createCoursePost(course, title, content, context).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${course.id}/discussion`);
                courseManagementRequest.createCoursePostReply(course, post, 'Answer Reply').then((response) => {
                    const answerPost = response.body;
                    cy.login(instructor, `/courses/${course.id}/discussion`);
                    cy.reload();
                    courseCommunication.showReplies(post.id);
                    courseCommunication.markAsAnswer(answerPost.id);
                    cy.reload();
                    courseCommunication.checkResolved(post.id);
                });
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
                const replyText = 'My Test reply';
                courseCommunication.openReply(post.id);
                courseCommunication.reply(post.id, replyText).then((intercept) => {
                    const reply = intercept.response?.body;
                    cy.login(studentOne, `/courses/${course.id}/discussion`);
                    courseCommunication.showReplies(post.id);
                    courseCommunication.checkReply(reply.id, replyText);
                });
            });
        });

        it('other students should be able to react to post', () => {
            const title = 'My React Test Post';
            const content = 'Test React Post Content';
            const context = CourseWideContext.RANDOM;
            cy.login(studentOne, `/courses/${course.id}/discussion`);
            courseManagementRequest.createCoursePost(course, title, content, context).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${course.id}/discussion`);
                const emoji = 'tada';
                courseCommunication.react(post.id, emoji);
                cy.reload();
                courseCommunication.checkReaction(post.id, emoji);
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

    describe('Exercise communication', () => {
        let textExercise: TextExercise;

        before('Create exercise', () => {
            cy.login(admin);
            courseManagementRequest.createTextExercise({ course }).then((response) => {
                textExercise = response.body;
            });
        });

        it('students should be able to create posts within exercises', () => {
            cy.login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
            courseCommunication.newPost();
            courseCommunication.setTitle('Exercise Test Post');
            cy.fixture('loremIpsum.txt').then((text) => {
                courseCommunication.setContent(text);
            });
            courseCommunication.save();
        });

        it('students should be able to search for exercise posts', () => {
            const title = 'Exercise Filter Test Post';
            const content = 'Exercise Filter Test Post Content';
            cy.login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
            courseManagementRequest.createCourseExercisePost(course, textExercise, title, content).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${course.id}/exercises/${textExercise.id}`);
                cy.reload();
                courseCommunication.searchForPost(title);
                courseCommunication.checkSingleExercisePost(post.id, title, content);
            });
        });

        it('other students should be able to reply to an exercise post', () => {
            const title = 'My Reply Test Post';
            const content = 'Test Reply Post Content';
            cy.login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
            courseManagementRequest.createCourseExercisePost(course, textExercise, title, content).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${course.id}/exercises/${textExercise.id}`);
                cy.reload();
                const replyText = 'My Test reply';
                courseCommunication.openReply(post.id);
                courseCommunication.reply(post.id, replyText).then((intercept) => {
                    const reply = intercept.response?.body;
                    cy.login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
                    courseCommunication.showReplies(post.id);
                    courseCommunication.checkReply(reply.id, replyText);
                });
            });
        });

        it('other students should be able to react to an exercise post', () => {
            const title = 'My React Test Post';
            const content = 'Test React Post Content';
            cy.login(studentOne, `/courses/${course.id}/discussion`);
            courseManagementRequest.createCourseExercisePost(course, textExercise, title, content).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${course.id}/exercises/${textExercise.id}`);
                const emoji = 'tada';
                courseCommunication.react(post.id, emoji);
                cy.reload();
                courseCommunication.checkReaction(post.id, emoji);
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
