import { Course } from 'app/entities/course.model';
import { Lecture } from 'app/entities/lecture.model';
import { Channel } from 'app/entities/metis/conversation/channel.model';
import { TextExercise } from 'app/entities/text-exercise.model';

import { communicationAPIRequest, courseCommunication, courseManagementAPIRequest, exerciseAPIRequest, navigationBar } from '../../support/artemis';
import { CourseWideContext } from '../../support/constants';
import { admin, instructor, studentOne, studentThree, studentTwo } from '../../support/users';
import { convertModelAfterMultiPart, titleCaseWord, titleLowercase } from '../../support/utils';

describe('Course communication', () => {
    let course: Course;
    let courseWithMessaging: Course;

    before('Create course', () => {
        cy.login(admin);

        courseManagementAPIRequest.createCourse({ allowMessaging: false }).then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementAPIRequest.addInstructorToCourse(course, instructor);
            courseManagementAPIRequest.addStudentToCourse(course, studentOne);
            courseManagementAPIRequest.addStudentToCourse(course, studentTwo);
            courseManagementAPIRequest.addStudentToCourse(course, studentThree);
        });

        courseManagementAPIRequest.createCourse().then((response) => {
            courseWithMessaging = convertModelAfterMultiPart(response);
            courseManagementAPIRequest.addInstructorToCourse(courseWithMessaging, instructor);
            courseManagementAPIRequest.addStudentToCourse(courseWithMessaging, studentOne);
            courseManagementAPIRequest.addStudentToCourse(courseWithMessaging, studentTwo);
            courseManagementAPIRequest.addStudentToCourse(courseWithMessaging, studentThree);
        });
    });

    describe('Course overview communication', () => {
        it('student should be able to create post', () => {
            cy.login(studentOne, `/courses/${course.id}/discussion`);
            courseCommunication.newPost();
            courseCommunication.selectContextInModal(CourseWideContext.ORGANIZATION);
            courseCommunication.setTitleInModal('Test Post');
            cy.fixture('loremIpsum-short.txt').then((text) => {
                courseCommunication.setContentInModal(text);
            });
            courseCommunication.save();
        });

        it('instructor should be able pin a post', () => {
            const title = 'Pin Test Post';
            const content = 'Pin Post Content';
            const context = CourseWideContext.RANDOM;
            cy.login(studentOne, `/courses/${course.id}/discussion`);
            communicationAPIRequest.createCoursePost(course, title, content, context).then((response) => {
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
            courseCommunication.getContextSelectorInModal().eq(0).should('not.contain', CourseWideContext.ANNOUNCEMENT);
        });

        it('instructor should be able to create announcement post', () => {
            const title = 'Announcement Test Post';
            const content = 'Announcement Post Content';
            cy.login(instructor, `/courses/${course.id}/discussion`);
            courseCommunication.newPost();
            courseCommunication.selectContextInModal(CourseWideContext.ANNOUNCEMENT);
            courseCommunication.setTitleInModal(title);
            courseCommunication.setContentInModal(content);
            courseCommunication.save();
            cy.login(studentTwo, `/courses/${course.id}/discussion`);
            navigationBar.openNotificationPanel();
            navigationBar.getNotifications().first().find('.notification-title').contains('New announcement');
            navigationBar
                .getNotifications()
                .first()
                .find('.notification-text')
                .contains((`The course "` + course.title + `" got a new announcement: "` + content + `"`).substring(0, 300 - 1));
        });

        it('instructor should be able to archive a post', () => {
            const title = 'Archive Test Post';
            const content = 'Archive Post Content';
            const context = CourseWideContext.RANDOM;
            cy.login(studentOne, `/courses/${course.id}/discussion`);
            communicationAPIRequest.createCoursePost(course, title, content, context).then((response) => {
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
            communicationAPIRequest.createCoursePost(course, title, content, context).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${course.id}/discussion`);
                communicationAPIRequest.createCoursePostReply(course, post, 'Answer Reply').then((response) => {
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
            communicationAPIRequest.createCoursePost(course, title, content, context).then((response) => {
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
            communicationAPIRequest.createCoursePost(course, title, content, context).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${course.id}/discussion`);
                cy.reload();
                courseCommunication.searchForPost(title);
                courseCommunication.checkSinglePost(post.id, title, content, context);
            });
        });

        it('other students should be able to filter for post by context', () => {
            const title = 'My Context Filter Test Post';
            const content = 'Test Context Filter Post Content';
            const context = CourseWideContext.RANDOM;
            cy.login(studentOne, `/courses/${course.id}/discussion`);
            communicationAPIRequest.createCoursePost(course, title, content, context).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${course.id}/discussion`);
                cy.reload();
                courseCommunication.filterByContext(titleCaseWord(context));
                courseCommunication.checkSinglePost(post.id, title, content, context);
            });
        });

        it('students should be able to filter for post by own', () => {
            const title = 'My Own Filter Test Post';
            const content = 'Test Own Filter Post Content';
            const context = CourseWideContext.RANDOM;
            cy.login(studentThree, `/courses/${course.id}/discussion`);
            communicationAPIRequest.createCoursePost(course, title, content, context).then((response) => {
                const post = response.body;
                courseCommunication.filterByOwn();
                courseCommunication.checkSinglePost(post.id, title, content, context);
            });
        });

        it('other students should be able to reply to post', () => {
            const title = 'My Reply Test Post';
            const content = 'Test Reply Post Content';
            const context = CourseWideContext.RANDOM;
            cy.login(studentOne, `/courses/${course.id}/discussion`);
            communicationAPIRequest.createCoursePost(course, title, content, context).then((response) => {
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
            communicationAPIRequest.createCoursePost(course, title, content, context).then((response) => {
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
            communicationAPIRequest.createCoursePost(course, title, content, context).then((response) => {
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
            communicationAPIRequest.createCoursePost(course, title, content, context).then((response) => {
                const post = response.body;
                cy.reload();
                courseCommunication.deletePost(post.id);
            });
        });
    });

    describe('Exercise communication via posts', () => {
        let textExercise: TextExercise;

        before('Create exercise', () => {
            cy.login(admin);
            exerciseAPIRequest.createTextExercise({ course }).then((response) => {
                textExercise = response.body;
            });
        });

        it('students should be able to create posts within exercises', () => {
            cy.login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
            courseCommunication.newPost();
            cy.fixture('loremIpsum-short.txt').then((text) => {
                courseCommunication.setContentInline(text);
            });
            courseCommunication.save();
        });

        it('instructor should be able pin a post within exercises', () => {
            const title = 'Pin Test Exercise Post';
            const content = 'Pin Exercise Post Content';
            cy.login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
            communicationAPIRequest.createCourseExercisePost(course, textExercise, title, content).then((response) => {
                const post = response.body;
                cy.login(instructor, `/courses/${course.id}/exercises/${textExercise.id}`);
                courseCommunication.pinPost(post.id);
                cy.reload();
                courseCommunication.checkSinglePostByPosition(0, undefined, content);
            });
        });

        it('instructor should be able to archive a post within exercises', () => {
            const title = 'Archive Test Exercise Post';
            const content = 'Archive Exercise Post Content';
            cy.login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
            communicationAPIRequest.createCourseExercisePost(course, textExercise, title, content).then((response) => {
                const post = response.body;
                cy.login(instructor, `/courses/${course.id}/exercises/${textExercise.id}`);
                courseCommunication.archivePost(post.id);
                cy.reload();
                courseCommunication.getSinglePost(post.id).should('not.exist');
            });
        });

        it('students should be able to search for exercise posts', () => {
            const title = 'Exercise Search Test Post';
            const content = 'Exercise Search Test Post Content';
            cy.login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
            communicationAPIRequest.createCourseExercisePost(course, textExercise, title, content).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${course.id}/exercises/${textExercise.id}`);
                cy.reload();
                courseCommunication.searchForPost(title);
                courseCommunication.checkSingleExercisePost(post.id, content);
            });
        });

        it('other students should be able to filter for exercise post by context', () => {
            const title = 'My Context Filter Test Exercise Post';
            const content = 'Test Context Filter Exercise Post Content';
            cy.login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
            communicationAPIRequest.createCourseExercisePost(course, textExercise, title, content).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${course.id}/discussion`);
                cy.reload();
                courseCommunication.filterByContext(textExercise.title!);
                courseCommunication.checkSinglePost(post.id, title, content);
            });
        });

        it('students should be able to filter for exercise post by own', () => {
            const title = 'My Own Filter Test Exercise Post';
            const content = 'Test Own Filter Exercise Post Content';
            cy.login(studentThree, `/courses/${course.id}/discussion`);
            communicationAPIRequest.createCourseExercisePost(course, textExercise, title, content).then((response) => {
                const post = response.body;
                courseCommunication.filterByOwn();
                courseCommunication.checkSinglePost(post.id, title, content);
            });
        });

        it('other students should be able to reply to an exercise post', () => {
            const title = 'My Reply Test Post';
            const content = 'Test Reply Post Content';
            cy.login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
            communicationAPIRequest.createCourseExercisePost(course, textExercise, title, content).then((response) => {
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
            communicationAPIRequest.createCourseExercisePost(course, textExercise, title, content).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${course.id}/exercises/${textExercise.id}`);
                const emoji = 'tada';
                courseCommunication.react(post.id, emoji);
                cy.reload();
                courseCommunication.checkReaction(post.id, emoji);
            });
        });
    });

    describe('Lecture communication via posts', () => {
        let lecture: Lecture;

        before('Create lecture', () => {
            cy.login(admin);
            courseManagementAPIRequest.createLecture(course).then((response) => {
                lecture = response.body;
            });
        });

        it('students should be able to create posts within lectures', () => {
            cy.login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
            courseCommunication.newPost();
            cy.fixture('loremIpsum-short.txt').then((text) => {
                courseCommunication.setContentInline(text);
            });
            courseCommunication.save();
        });

        it('instructor should be able pin a post within lectures', () => {
            const title = 'Pin Test Lecture Post';
            const content = 'Pin Lecture Post Content';
            cy.login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
            communicationAPIRequest.createCourseLecturePost(course, lecture, title, content).then((response) => {
                const post = response.body;
                cy.login(instructor, `/courses/${course.id}/lectures/${lecture.id}`);
                courseCommunication.pinPost(post.id);
                cy.reload();
                courseCommunication.checkSinglePostByPosition(0, undefined, content);
            });
        });

        it('instructor should be able to archive a post within lectures', () => {
            const title = 'Archive Test Lecture Post';
            const content = 'Archive Lecture Post Content';
            cy.login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
            communicationAPIRequest.createCourseLecturePost(course, lecture, title, content).then((response) => {
                const post = response.body;
                cy.login(instructor, `/courses/${course.id}/lectures/${lecture.id}`);
                courseCommunication.archivePost(post.id);
                cy.reload();
                courseCommunication.getSinglePost(post.id).should('not.exist');
            });
        });

        it('students should be able to search for lecture posts', () => {
            const title = 'Exercise Search Test Post';
            const content = 'Exercise Search Test Post Content';
            cy.login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
            communicationAPIRequest.createCourseLecturePost(course, lecture, title, content).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${course.id}/lectures/${lecture.id}`);
                cy.reload();
                courseCommunication.searchForPost(title);
                courseCommunication.checkSingleExercisePost(post.id, content);
            });
        });

        it('other students should be able to filter for lecture post by context', () => {
            const title = 'My Context Filter Test Lecture Post';
            const content = 'Test Context Filter Lecture Post Content';
            cy.login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
            communicationAPIRequest.createCourseLecturePost(course, lecture, title, content).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${course.id}/discussion`);
                cy.reload();
                courseCommunication.filterByContext(lecture.title!);
                courseCommunication.checkSinglePost(post.id, title, content);
            });
        });

        it('students should be able to filter for lecture post by own', () => {
            const title = 'My Own Filter Test Lecture Post';
            const content = 'Test Own Filter Lecture Post Content';
            cy.login(studentThree, `/courses/${course.id}/discussion`);
            communicationAPIRequest.createCourseLecturePost(course, lecture, title, content).then((response) => {
                const post = response.body;
                courseCommunication.filterByOwn();
                courseCommunication.checkSinglePost(post.id, title, content);
            });
        });

        it('other students should be able to reply to a lecture post', () => {
            const title = 'My Reply Test Post';
            const content = 'Test Reply Post Content';
            cy.login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
            communicationAPIRequest.createCourseLecturePost(course, lecture, title, content).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${course.id}/lectures/${lecture.id}`);
                cy.reload();
                const replyText = 'My Test reply';
                courseCommunication.openReply(post.id);
                courseCommunication.reply(post.id, replyText).then((intercept) => {
                    const reply = intercept.response?.body;
                    cy.login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
                    courseCommunication.showReplies(post.id);
                    courseCommunication.checkReply(reply.id, replyText);
                });
            });
        });

        it('other students should be able to react to a lecture post', () => {
            const title = 'My React Test Post';
            const content = 'Test React Post Content';
            cy.login(studentOne, `/courses/${course.id}/discussion`);
            communicationAPIRequest.createCourseLecturePost(course, lecture, title, content).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${course.id}/lectures/${lecture.id}`);
                const emoji = 'tada';
                courseCommunication.react(post.id, emoji);
                cy.reload();
                courseCommunication.checkReaction(post.id, emoji);
            });
        });
    });

    describe('Exercise communication via channel', () => {
        let textExercise: TextExercise;
        let channel: Channel;

        before('Create exercise', () => {
            cy.login(admin);
            exerciseAPIRequest.createTextExercise({ course: courseWithMessaging }).then((response) => {
                textExercise = response.body;
                textExercise.channelName = 'exercise-' + titleLowercase(textExercise.title!);
                communicationAPIRequest.getExerciseChannel(textExercise.course!.id!, textExercise.id!).then((response) => {
                    channel = response.body;
                });
            });
        });

        it('students should be able to create messages within exercises', () => {
            cy.login(studentOne, `/courses/${courseWithMessaging.id}/exercises/${textExercise.id}`);
            courseCommunication.newPost();
            cy.fixture('loremIpsum-short.txt').then((text) => {
                courseCommunication.setContentInline(text);
            });
            courseCommunication.saveMessage();
        });

        it('students should be able to search for exercise posts', () => {
            const title = 'Exercise Search Test Post';
            const content = 'Exercise Search Test Post Content';
            cy.login(studentOne, `/courses/${courseWithMessaging.id}/exercises/${textExercise.id}`);
            communicationAPIRequest.createCourseMessage(courseWithMessaging, channel.id!, 'channel', title + content).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${courseWithMessaging.id}/exercises/${textExercise.id}`);
                cy.reload();
                courseCommunication.searchForPost(title);
                courseCommunication.checkSingleExercisePost(post.id, content);
            });
        });

        it('other students should be able to reply to an exercise post', () => {
            const title = 'My Reply Test Post';
            const content = 'Test Reply Post Content';
            cy.login(studentOne, `/courses/${courseWithMessaging.id}/exercises/${textExercise.id}`);
            communicationAPIRequest.createCourseMessage(courseWithMessaging, channel.id!, 'channel', title + content).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${courseWithMessaging.id}/exercises/${textExercise.id}`);
                cy.reload();
                const replyText = 'My Test reply';
                courseCommunication.openReply(post.id);
                courseCommunication.replyWithMessage(post.id, replyText).then((intercept) => {
                    const reply = intercept.response?.body;
                    cy.login(studentOne, `/courses/${courseWithMessaging.id}/exercises/${textExercise.id}`);
                    courseCommunication.showReplies(post.id);
                    courseCommunication.checkReply(reply.id, replyText);
                });
            });
        });

        it('other students should be able to react to an exercise post', () => {
            const title = 'My React Test Post';
            const content = 'Test React Post Content';
            cy.login(studentOne, `/courses/${course.id}/discussion`);
            communicationAPIRequest.createCourseMessage(courseWithMessaging, channel.id!, 'channel', title + content).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${courseWithMessaging.id}/exercises/${textExercise.id}`);
                const emoji = 'tada';
                courseCommunication.react(post.id, emoji);
                cy.reload();
                courseCommunication.checkReaction(post.id, emoji);
            });
        });
    });

    describe('Lecture communication via channel', () => {
        let lecture: Lecture;
        let channel: Channel;

        before('Create lecture', () => {
            cy.login(admin);
            courseManagementAPIRequest.createLecture(courseWithMessaging).then((response) => {
                lecture = response.body;
                lecture.channelName = 'lecture-' + titleLowercase(lecture.title!);
                communicationAPIRequest.getLectureChannel(lecture.course!.id!, lecture.id!).then((response) => {
                    channel = response.body;
                });
            });
        });

        it('students should be able to create messages within lecture', () => {
            cy.login(studentOne, `/courses/${courseWithMessaging.id}/lectures/${lecture.id}`);
            courseCommunication.newPost();
            cy.fixture('loremIpsum-short.txt').then((text) => {
                courseCommunication.setContentInline(text);
            });
            courseCommunication.saveMessage();
        });

        it('students should be able to search for lecture posts', () => {
            const title = 'Lecture Search Test Post';
            const content = 'Lecture Search Test Post Content';
            cy.login(studentOne, `/courses/${courseWithMessaging.id}/lectures/${lecture.id}`);
            communicationAPIRequest.createCourseMessage(courseWithMessaging, channel.id!, 'channel', title + content).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${courseWithMessaging.id}/lectures/${lecture.id}`);
                cy.reload();
                courseCommunication.searchForPost(title);
                courseCommunication.checkSingleExercisePost(post.id, content);
            });
        });

        it('other students should be able to reply to a lecture post', () => {
            const title = 'My Reply Test Post';
            const content = 'Test Reply Post Content';
            cy.login(studentOne, `/courses/${courseWithMessaging.id}/lectures/${lecture.id}`);
            communicationAPIRequest.createCourseMessage(courseWithMessaging, channel.id!, 'channel', title + content).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${courseWithMessaging.id}/lectures/${lecture.id}`);
                cy.reload();
                const replyText = 'My Test reply';
                courseCommunication.openReply(post.id);
                courseCommunication.replyWithMessage(post.id, replyText).then((intercept) => {
                    const reply = intercept.response?.body;
                    cy.login(studentOne, `/courses/${courseWithMessaging.id}/lectures/${lecture.id}`);
                    courseCommunication.showReplies(post.id);
                    courseCommunication.checkReply(reply.id, replyText);
                });
            });
        });

        it('other students should be able to react to a lecture post', () => {
            const title = 'My React Test Post';
            const content = 'Test React Post Content';
            cy.login(studentOne, `/courses/${course.id}/discussion`);
            communicationAPIRequest.createCourseMessage(courseWithMessaging, channel.id!, 'channel', title + content).then((response) => {
                const post = response.body;
                cy.login(studentTwo, `/courses/${courseWithMessaging.id}/lectures/${lecture.id}`);
                const emoji = 'tada';
                courseCommunication.react(post.id, emoji);
                cy.reload();
                courseCommunication.checkReaction(post.id, emoji);
            });
        });
    });

    after('Delete Courses', () => {
        courseManagementAPIRequest.deleteCourse(course, admin);
        courseManagementAPIRequest.deleteCourse(courseWithMessaging, admin);
    });
});
