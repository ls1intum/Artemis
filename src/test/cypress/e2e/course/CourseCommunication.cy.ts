import { Course } from 'app/entities/course.model';
import { Lecture } from 'app/entities/lecture.model';
import { Channel, ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { TextExercise } from 'app/entities/text-exercise.model';

import { communicationAPIRequest, courseCommunication, courseManagementAPIRequest, exerciseAPIRequest } from '../../support/artemis';
import { admin, instructor, studentOne, studentThree, studentTwo } from '../../support/users';
import { convertModelAfterMultiPart, titleLowercase } from '../../support/utils';
import { COURSE_BASE, GET } from '../../support/constants';

const courseConfigsToTest = [
    { description: 'messaging and communication enabled', config: { allowMessaging: true, allowCommunication: true } },
    { description: 'only communication enabled', config: { allowMessaging: false, allowCommunication: true } },
];

courseConfigsToTest.forEach((configToTest) => {
    describe('Course communication with ' + configToTest.description, () => {
        let course: Course;
        let courseWideRandomChannel: ChannelDTO;

        before('Create course', () => {
            cy.login(admin);

            courseManagementAPIRequest.createCourse(configToTest.config).then((response) => {
                course = convertModelAfterMultiPart(response);
                courseManagementAPIRequest.addInstructorToCourse(course, instructor);
                courseManagementAPIRequest.addStudentToCourse(course, studentOne);
                courseManagementAPIRequest.addStudentToCourse(course, studentTwo);
                courseManagementAPIRequest.addStudentToCourse(course, studentThree);

                communicationAPIRequest.getCourseWideChannels(course.id!).then((courseWideChannels) => {
                    courseWideRandomChannel = courseWideChannels.find((channel) => channel.name === 'random')!;
                });
            });
        });

        describe('Course overview communication', () => {
            it('instructor should be able pin a message', () => {
                const content = 'Pin Post Content';
                cy.login(studentOne, `/courses/${course.id}/discussion`);
                communicationAPIRequest.createCourseWideMessage(course, courseWideRandomChannel.id!, content).then((response) => {
                    const post = response.body;
                    cy.login(instructor, `/courses/${course.id}/discussion`);
                    courseCommunication.pinPost(post.id);
                    cy.reload();
                    courseCommunication.checkSinglePostByPosition(0, undefined, content);
                });
            });

            it('instructor should be able to select answer', () => {
                const content = 'Answer Post Content';
                cy.login(studentOne, `/courses/${course.id}/discussion`);
                communicationAPIRequest.createCourseWideMessage(course, courseWideRandomChannel.id!, content).then((response) => {
                    const post = response.body;
                    cy.login(studentTwo, `/courses/${course.id}/discussion`);
                    communicationAPIRequest.createCourseMessageReply(course, post, 'Answer Reply').then((response) => {
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

            it('other students should be able to see message', () => {
                const content = 'Test Post Content';
                cy.login(studentOne, `/courses/${course.id}/discussion`);
                communicationAPIRequest.createCourseWideMessage(course, courseWideRandomChannel.id!, content).then((response) => {
                    const post = response.body;
                    cy.login(studentTwo, `/courses/${course.id}/discussion`);
                    cy.reload();
                    courseCommunication.checkSinglePost(post.id, content);
                });
            });

            it('other students should be able to search for message', () => {
                const content = 'Test Search Post Content';
                cy.login(studentOne, `/courses/${course.id}/discussion`);
                communicationAPIRequest.createCourseWideMessage(course, courseWideRandomChannel.id!, content).then((response) => {
                    const post = response.body;
                    cy.login(studentTwo, `/courses/${course.id}/discussion`);
                    cy.reload();
                    courseCommunication.searchForMessage(content);
                    courseCommunication.checkSinglePost(post.id, content);
                });
            });

            it('other students should be able to filter for message by context', () => {
                const content = 'Test Context Filter Post Content';
                cy.intercept(GET, `${COURSE_BASE}/*/conversations`).as('getConversations');
                cy.login(studentOne, `/courses/${course.id}/discussion`);
                cy.wait('@getConversations');
                communicationAPIRequest.createCourseWideMessage(course, courseWideRandomChannel.id!, content).then((response) => {
                    const post = response.body;
                    cy.intercept(GET, `${COURSE_BASE}/*/conversations`).as('getConversations');
                    cy.reload();
                    cy.wait('@getConversations');
                    courseCommunication.filterByContext(courseWideRandomChannel.name!);
                    courseCommunication.checkSinglePost(post.id, content);
                });
            });

            it('students should be able to filter for message by own', () => {
                const content = 'Test Own Filter Post Content';
                cy.login(studentThree, `/courses/${course.id}/discussion`);
                communicationAPIRequest.createCourseWideMessage(course, courseWideRandomChannel.id!, content).then((response) => {
                    const post = response.body;
                    courseCommunication.filterByOwn();
                    courseCommunication.checkSinglePost(post.id, content);
                });
            });

            it('other students should be able to reply to message', () => {
                const content = 'Test Reply Post Content';
                cy.login(studentOne, `/courses/${course.id}/discussion`);
                communicationAPIRequest.createCourseWideMessage(course, courseWideRandomChannel.id!, content).then((response) => {
                    const post = response.body;
                    cy.login(studentTwo, `/courses/${course.id}/discussion`);
                    cy.reload();
                    const replyText = 'My Test reply';
                    courseCommunication.openReply(post.id);
                    courseCommunication.replyWithMessage(post.id, replyText).then((intercept) => {
                        const reply = intercept.response?.body;
                        cy.login(studentOne, `/courses/${course.id}/discussion`);
                        courseCommunication.showReplies(post.id);
                        courseCommunication.checkReply(reply.id, replyText);
                    });
                });
            });

            it('other students should be able to react to message', () => {
                const content = 'Test React Post Content';
                cy.login(studentOne, `/courses/${course.id}/discussion`);
                communicationAPIRequest.createCourseWideMessage(course, courseWideRandomChannel.id!, content).then((response) => {
                    const post = response.body;
                    cy.login(studentTwo, `/courses/${course.id}/discussion`);
                    const emoji = 'tada';
                    courseCommunication.react(post.id, emoji);
                    cy.reload();
                    courseCommunication.checkReaction(post.id, emoji);
                });
            });
            it('students should be able to edit their message', () => {
                const content = 'Test Edit Post Content';
                const newContent = 'Test Edited Post Content';
                cy.login(studentOne, `/courses/${course.id}/discussion`);
                communicationAPIRequest.createCourseWideMessage(course, courseWideRandomChannel.id!, content).then((response) => {
                    const post = response.body;
                    cy.reload();
                    courseCommunication.editMessage(post.id, newContent);
                    courseCommunication.checkSinglePost(post.id, newContent);
                });
            });

            it('students should be able to delete their message', () => {
                const content = 'Test Delete Post Content';
                cy.login(studentOne, `/courses/${course.id}/discussion`);
                communicationAPIRequest.createCourseWideMessage(course, courseWideRandomChannel.id!, content).then((response) => {
                    const post = response.body;
                    cy.reload();
                    courseCommunication.deletePost(post.id);
                });
            });
        });

        describe('Exercise communication via channel', () => {
            let textExercise: TextExercise;
            let channel: Channel;

            before('Create exercise', () => {
                cy.login(admin);
                exerciseAPIRequest.createTextExercise({ course: course }).then((response) => {
                    textExercise = response.body;
                    textExercise.channelName = 'exercise-' + titleLowercase(textExercise.title!);
                    communicationAPIRequest.getExerciseChannel(textExercise.course!.id!, textExercise.id!).then((response) => {
                        channel = response.body;
                    });
                });
            });

            it('instructor should be able pin a message within exercises', () => {
                const content = 'Pin Exercise Post Content';
                cy.login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
                communicationAPIRequest.createCourseWideMessage(course, channel.id!, content).then((response) => {
                    const post = response.body;
                    cy.login(instructor, `/courses/${course.id}/exercises/${textExercise.id}`);
                    courseCommunication.pinPost(post.id);
                    cy.reload();
                    courseCommunication.checkSinglePostByPosition(0, undefined, content);
                });
            });

            it('students should be able to create messages within exercises', () => {
                cy.login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
                courseCommunication.newPost();
                cy.fixture('loremIpsum-short.txt').then((text) => {
                    courseCommunication.setContentInline(text);
                });
                courseCommunication.saveMessage();
            });

            it('students should be able to search for exercise messages', () => {
                const content = 'Exercise Search Test Post Content';
                cy.login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
                communicationAPIRequest.createCourseMessage(course, channel.id!, 'channel', content).then((response) => {
                    const post = response.body;
                    cy.login(studentTwo, `/courses/${course.id}/exercises/${textExercise.id}`);
                    cy.reload();
                    courseCommunication.searchForMessage(content);
                    courseCommunication.checkSingleExercisePost(post.id, content);
                });
            });

            it('other students should be able to filter for exercise message by context', () => {
                const content = 'Test Context Filter Exercise Post Content';
                cy.login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
                communicationAPIRequest.createCourseWideMessage(course, channel.id!, content).then((response) => {
                    const post = response.body;
                    cy.intercept(GET, `${COURSE_BASE}/*/conversations`).as('getConversations');
                    cy.login(studentTwo, `/courses/${course.id}/discussion`);
                    cy.wait('@getConversations');
                    courseCommunication.filterByContext(textExercise.channelName!);
                    courseCommunication.checkSinglePost(post.id, content);
                });
            });

            it('students should be able to filter for exercise message by own', () => {
                const content = 'Test Own Filter Exercise Post Content';
                cy.login(studentThree, `/courses/${course.id}/discussion`);
                communicationAPIRequest.createCourseWideMessage(course, channel.id!, content).then((response) => {
                    const post = response.body;
                    courseCommunication.filterByOwn();
                    courseCommunication.checkSinglePost(post.id, content);
                });
            });

            it('other students should be able to reply to an exercise message', () => {
                const content = 'Test Reply Post Content';
                cy.login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
                communicationAPIRequest.createCourseMessage(course, channel.id!, 'channel', content).then((response) => {
                    const post = response.body;
                    cy.login(studentTwo, `/courses/${course.id}/exercises/${textExercise.id}`);
                    cy.reload();
                    const replyText = 'My Test reply';
                    courseCommunication.openReply(post.id);
                    courseCommunication.replyWithMessage(post.id, replyText).then((intercept) => {
                        const reply = intercept.response?.body;
                        cy.login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
                        courseCommunication.showReplies(post.id);
                        courseCommunication.checkReply(reply.id, replyText);
                    });
                });
            });

            it('other students should be able to react to an exercise message', () => {
                const content = 'Test React Post Content';
                cy.login(studentOne, `/courses/${course.id}/discussion`);
                communicationAPIRequest.createCourseMessage(course, channel.id!, 'channel', content).then((response) => {
                    const post = response.body;
                    cy.login(studentTwo, `/courses/${course.id}/exercises/${textExercise.id}`);
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
                courseManagementAPIRequest.createLecture(course).then((response) => {
                    lecture = response.body;
                    lecture.channelName = 'lecture-' + titleLowercase(lecture.title!);
                    communicationAPIRequest.getLectureChannel(lecture.course!.id!, lecture.id!).then((response) => {
                        channel = response.body;
                    });
                });
            });

            it('instructor should be able pin a message within lectures', () => {
                const content = 'Pin Lecture Post Content';
                cy.login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
                communicationAPIRequest.createCourseWideMessage(course, channel.id!, content).then((response) => {
                    const post = response.body;
                    cy.login(instructor, `/courses/${course.id}/lectures/${lecture.id}`);
                    courseCommunication.pinPost(post.id);
                    cy.reload();
                    courseCommunication.checkSinglePostByPosition(0, undefined, content);
                });
            });

            it('students should be able to create messages within lecture', () => {
                cy.login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
                courseCommunication.newPost();
                cy.fixture('loremIpsum-short.txt').then((text) => {
                    courseCommunication.setContentInline(text);
                });
                courseCommunication.saveMessage();
            });

            it('students should be able to search for lecture messages', () => {
                const content = 'Lecture Search Test Post Content';
                cy.login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
                communicationAPIRequest.createCourseWideMessage(course, channel.id!, content).then((response) => {
                    const post = response.body;
                    cy.login(studentTwo, `/courses/${course.id}/lectures/${lecture.id}`);
                    cy.reload();
                    courseCommunication.searchForMessage(content);
                    courseCommunication.checkSingleExercisePost(post.id, content);
                });
            });

            it('other students should be able to filter for lecture message by context', () => {
                const content = 'Test Context Filter Lecture Post Content';
                cy.login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
                communicationAPIRequest.createCourseWideMessage(course, channel.id!, content).then((response) => {
                    const post = response.body;
                    cy.intercept(GET, `${COURSE_BASE}/*/conversations`).as('getConversations');
                    cy.login(studentTwo, `/courses/${course.id}/discussion`);
                    cy.wait('@getConversations');
                    courseCommunication.filterByContext(lecture.channelName!);
                    courseCommunication.checkSinglePost(post.id, content);
                });
            });

            it('students should be able to filter for lecture message by own', () => {
                const content = 'Test Own Filter Lecture Post Content';
                cy.login(studentThree, `/courses/${course.id}/discussion`);
                communicationAPIRequest.createCourseWideMessage(course, channel.id!, content).then((response) => {
                    const post = response.body;
                    courseCommunication.filterByOwn();
                    courseCommunication.checkSinglePost(post.id, content);
                });
            });

            it('other students should be able to reply to a lecture message', () => {
                const content = 'Test Reply Post Content';
                cy.login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
                communicationAPIRequest.createCourseWideMessage(course, channel.id!, content).then((response) => {
                    const post = response.body;
                    cy.login(studentTwo, `/courses/${course.id}/lectures/${lecture.id}`);
                    cy.reload();
                    const replyText = 'My Test reply';
                    courseCommunication.openReply(post.id);
                    courseCommunication.replyWithMessage(post.id, replyText).then((intercept) => {
                        const reply = intercept.response?.body;
                        cy.login(studentOne, `/courses/${course.id}/lectures/${lecture.id}`);
                        courseCommunication.showReplies(post.id);
                        courseCommunication.checkReply(reply.id, replyText);
                    });
                });
            });

            it('other students should be able to react to a lecture message', () => {
                const content = 'Test React Post Content';
                cy.login(studentOne, `/courses/${course.id}/discussion`);
                communicationAPIRequest.createCourseWideMessage(course, channel.id!, content).then((response) => {
                    const post = response.body;
                    cy.login(studentTwo, `/courses/${course.id}/lectures/${lecture.id}`);
                    const emoji = 'tada';
                    courseCommunication.react(post.id, emoji);
                    cy.reload();
                    courseCommunication.checkReaction(post.id, emoji);
                });
            });
        });

        after('Delete Courses', () => {
            courseManagementAPIRequest.deleteCourse(course, admin);
        });
    });
});
