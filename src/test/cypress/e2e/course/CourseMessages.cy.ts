import { Channel } from 'app/entities/metis/conversation/channel.model';
import { Course } from 'app/entities/course.model';
import { GroupChat } from 'app/entities/metis/conversation/group-chat.model';
import { courseManagementRequest, courseMessages } from '../../support/artemis';
import { ExamBuilder, convertModelAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import { admin, instructor, studentOne, studentTwo, tutor, users } from '../../support/users';
import { titleLowercase } from '../../support/utils';

describe('Course messages', () => {
    let course: Course;

    before('Create course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementRequest.addInstructorToCourse(course, instructor);
            courseManagementRequest.addTutorToCourse(course, tutor);
            courseManagementRequest.addStudentToCourse(course, studentOne);
            courseManagementRequest.addStudentToCourse(course, studentTwo);
        });
    });

    describe('Channel messages', () => {
        describe('Create channel', () => {
            it('check for pre-created channels', () => {
                cy.login(instructor, `/courses/${course.id}/messages`);
                courseMessages.browseChannelsButton();
                courseMessages.checkChannelsExists('tech-support');
                courseMessages.checkChannelsExists('organization');
                courseMessages.checkChannelsExists('random');
                courseMessages.checkChannelsExists('announcement');
            });

            it('instructors should be able to create public announcement channel', () => {
                cy.login(instructor, `/courses/${course.id}/messages`);
                const name = 'public-ancmnt-ch';
                courseMessages.createChannelButton();
                courseMessages.setName(name);
                courseMessages.setDescription('A public announcement channel');
                courseMessages.setPublic();
                courseMessages.setAnnouncementChannel();
                courseMessages.createChannel(true, true);
                courseMessages.getName().contains(name);
            });

            it('instructors should be able to create private announcement channel', () => {
                cy.login(instructor, `/courses/${course.id}/messages`);
                const name = 'private-ancmnt-ch';
                courseMessages.createChannelButton();
                courseMessages.setName(name);
                courseMessages.setDescription('A private announcement channel');
                courseMessages.setPrivate();
                courseMessages.setAnnouncementChannel();
                courseMessages.createChannel(true, false);
                courseMessages.getName().contains(name);
            });

            it('instructors should be able to create public unrestricted channel', () => {
                cy.login(instructor, `/courses/${course.id}/messages`);
                const name = 'public-unrstct-ch';
                courseMessages.createChannelButton();
                courseMessages.setName(name);
                courseMessages.setDescription('A public unrestricted channel');
                courseMessages.setPublic();
                courseMessages.setUnrestrictedChannel();
                courseMessages.createChannel(false, true);
                courseMessages.getName().contains(name);
            });

            it('instructors should be able to create private unrestricted channel', () => {
                cy.login(instructor, `/courses/${course.id}/messages`);
                const name = 'private-unrstct-ch';
                courseMessages.createChannelButton();
                courseMessages.setName(name);
                courseMessages.setDescription('A public unrestricted channel');
                courseMessages.setPrivate();
                courseMessages.setUnrestrictedChannel();
                courseMessages.createChannel(false, false);
                courseMessages.getName().contains(name);
            });

            it('instructors should not be able to create channel with uppercase name', () => {
                cy.login(instructor, `/courses/${course.id}/messages`);
                const name = 'Forbidden Name';
                courseMessages.createChannelButton();
                courseMessages.setName(name);
                courseMessages.getError().contains('Names can only contain lowercase letters');
            });

            it('instructors should not be able to create channel with name longer than 30 chars', () => {
                cy.login(instructor, `/courses/${course.id}/messages`);
                const name = 'way-way-way-too-long-channel-title';
                courseMessages.createChannelButton();
                courseMessages.setName(name);
                courseMessages.getError().contains('Name can be max 30 characters long!');
            });

            it('check that channel is created, when a lecture is created', () => {
                cy.login(admin);
                courseManagementRequest.createLecture(course, 'Test Lecture');
                cy.login(instructor, `/courses/${course.id}/messages`);
                courseMessages.browseLectureChannelsButton();
                courseMessages.checkChannelsExists('lecture-test-lecture');
            });

            it('check that channel is created, when an exercise is created', () => {
                cy.login(admin);
                courseManagementRequest.createTextExercise({ course }, 'Test Exercise');
                cy.login(instructor, `/courses/${course.id}/messages`);
                courseMessages.browseExerciseChannelsButton();
                courseMessages.checkChannelsExists('exercise-test-exercise');
            });

            it('check that channel is created, when an exam is created', () => {
                cy.login(admin);
                const examContent = new ExamBuilder(course).build();
                courseManagementRequest.createExam(examContent);
                cy.login(instructor, `/courses/${course.id}/messages`);
                courseMessages.browseExamChannelsButton();
                courseMessages.checkChannelsExists(titleLowercase(examContent.title));
            });
        });

        describe('Edit channel', () => {
            let channel: Channel;
            before('create channel', () => {
                cy.login(admin);
                courseManagementRequest.createCourseMessageChannel(course, 'test-channel', 'Test Channel', true, true).then((response) => {
                    channel = response.body;
                    courseManagementRequest.joinUserIntoChannel(course, channel, instructor);
                });
            });

            it('instructors should be able to edit a channel', () => {
                cy.login(instructor, `/courses/${course.id}/messages?conversationId=${channel.id}`);
                const newName = 'new-test-name';
                const topic = 'test-topic';
                courseMessages.getName().click();
                courseMessages.editName(newName);
                courseMessages.editTopic(topic);
                courseMessages.editDescription('New Description');
                courseMessages.closeEditPanel();
                courseMessages.getName().contains(newName);
                courseMessages.getTopic().contains(topic);
            });
        });

        describe('Join channel', () => {
            let channel: Channel;
            before('create channel', () => {
                cy.login(admin);
                courseManagementRequest.createCourseMessageChannel(course, 'join-test-channel', 'Join Test Channel', true, true).then((response) => {
                    channel = response.body;
                });
            });

            it('student should be joined into pre-created channels automatically', () => {
                cy.login(studentOne, `/courses/${course.id}/messages`);
                courseMessages.browseChannelsButton();
                courseMessages.getChannelIdByName('tech-support').then((response) => {
                    const techSupportChannelId = Number(response!);
                    courseMessages.checkBadgeJoined(techSupportChannelId).should('exist').contains('Joined');
                });
                courseMessages.getChannelIdByName('random').then((response) => {
                    const techSupportChannelId = Number(response!);
                    courseMessages.checkBadgeJoined(techSupportChannelId).should('exist').contains('Joined');
                });
                courseMessages.getChannelIdByName('announcement').then((response) => {
                    const techSupportChannelId = Number(response!);
                    courseMessages.checkBadgeJoined(techSupportChannelId).should('exist').contains('Joined');
                });
                courseMessages.getChannelIdByName('organization').then((response) => {
                    const techSupportChannelId = Number(response!);
                    courseMessages.checkBadgeJoined(techSupportChannelId).should('exist').contains('Joined');
                });
            });

            it('student should be able to join a public channel', () => {
                cy.login(studentOne, `/courses/${course.id}/messages`);
                courseMessages.browseChannelsButton();
                courseMessages.joinChannel(channel.id!);
                courseMessages.checkBadgeJoined(channel.id!).should('exist').contains('Joined');
            });

            it('student should be able to leave a public channel', () => {
                cy.login(studentOne, `/courses/${course.id}/messages`);
                courseMessages.browseChannelsButton();
                courseMessages.leaveChannel(channel.id!);
                courseMessages.checkBadgeJoined(channel.id!).should('not.exist');
            });
        });

        describe('Write/edit/delete message in channel', () => {
            let channel: Channel;
            before('create channel', () => {
                cy.login(admin);
                courseManagementRequest.createCourseMessageChannel(course, 'write-test-channel', 'Write Test Channel', false, true).then((response) => {
                    channel = response.body;
                    courseManagementRequest.joinUserIntoChannel(course, channel, studentOne);
                });
            });

            it('student should be able to write message in channel', () => {
                cy.login(studentOne, `/courses/${course.id}/messages?conversationId=${channel.id}`);
                const messageText = 'Student Test Message';
                courseMessages.writeMessage(messageText);
                courseMessages.save().then((interception) => {
                    const message = interception.response!.body;
                    courseMessages.checkMessage(message.id, messageText);
                });
            });

            it('student should be able to edit message in channel', () => {
                cy.login(studentOne, `/courses/${course.id}/messages?conversationId=${channel.id}`);
                const messageText = 'Student Edit Test Message';
                courseManagementRequest.createCourseMessage(course, channel.id!, 'channel', messageText).then((response) => {
                    const message = response.body;
                    const newMessage = 'Edited Text';
                    courseMessages.editMessage(message.id, newMessage);
                    courseMessages.checkMessage(message.id, newMessage);
                    courseMessages.getSinglePost(message.id).find('.edited-text').should('exist');
                });
            });

            it('student should be able to delete his message in channel', () => {
                cy.login(studentOne, `/courses/${course.id}/messages?conversationId=${channel.id}`);
                const messageText = 'Student Edit Test Message';
                courseManagementRequest.createCourseMessage(course, channel.id!, 'channel', messageText).then((response) => {
                    const message = response.body;
                    courseMessages.checkMessage(message.id, messageText);
                    courseMessages.deleteMessage(message.id);
                    courseMessages.getSinglePost(message.id).should('not.exist');
                });
            });
        });
    });

    describe('Group chats', () => {
        let instructorName: string;
        let tutorName: string;
        let studentOneName: string;
        let studentTwoName: string;

        before('Get usernames', () => {
            cy.login(admin);
            users.getUserInfo(instructor.username, (userInfo) => {
                instructorName = userInfo.name;
            });
            users.getUserInfo(tutor.username, (userInfo) => {
                tutorName = userInfo.name;
            });
            users.getUserInfo(studentOne.username, (userInfo) => {
                studentOneName = userInfo.name;
            });
            users.getUserInfo(studentTwo.username, (userInfo) => {
                studentTwoName = userInfo.name;
            });
        });

        describe('Create group chat', () => {
            it('instructors should be able to create group chat', () => {
                cy.login(instructor, `/courses/${course.id}/messages`);
                courseMessages.createGroupChatButton();
                courseMessages.addUserToGroupChat(studentOne.username);
                courseMessages.addUserToGroupChat(studentTwo.username);
                courseMessages.createGroupChat().then((interception) => {
                    const group = interception.response!.body;
                    courseMessages.listMembersButton(course.id!, group.id);
                    courseMessages.checkMemberList(studentOneName);
                    courseMessages.checkMemberList(studentTwoName);
                    courseMessages.closeEditPanel();
                });
            });

            it('tutor should be able to create group chat', () => {
                cy.login(tutor, `/courses/${course.id}/messages`);
                courseMessages.createGroupChatButton();
                courseMessages.addUserToGroupChat(studentOne.username);
                courseMessages.addUserToGroupChat(instructor.username);
                courseMessages.createGroupChat().then((interception) => {
                    const group = interception.response!.body;
                    courseMessages.listMembersButton(course.id!, group.id);
                    courseMessages.checkMemberList(studentOneName);
                    courseMessages.checkMemberList(instructorName);
                    courseMessages.closeEditPanel();
                });
            });

            it('student should be able to create group chat', () => {
                cy.login(studentOne, `/courses/${course.id}/messages`);
                courseMessages.createGroupChatButton();
                courseMessages.addUserToGroupChat(studentTwo.username);
                courseMessages.addUserToGroupChat(tutor.username);
                courseMessages.createGroupChat().then((interception) => {
                    const group = interception.response!.body;
                    courseMessages.listMembersButton(course.id!, group.id);
                    courseMessages.checkMemberList(studentTwoName);
                    courseMessages.checkMemberList(tutorName);
                    courseMessages.closeEditPanel();
                });
            });
        });

        describe('Add to group chat', () => {
            let groupChat: GroupChat;
            before('create group chat', () => {
                cy.login(admin);
                courseManagementRequest.createCourseMessageGroupChat(course, [studentOne.username, tutor.username]).then((response) => {
                    groupChat = response.body;
                });
            });

            it('tutor should be able to add user to group chat', () => {
                cy.login(tutor, `/courses/${course.id}/messages?conversationId=${groupChat.id}`);
                courseMessages.addUserToGroupChatButton();
                courseMessages.addUserToGroupChat(instructor.username);
                courseMessages.updateGroupChat();

                courseMessages.listMembersButton(course.id!, groupChat.id!);
                courseMessages.checkMemberList(instructorName);
                courseMessages.closeEditPanel();
            });

            it('student should be able to add user to group chat', () => {
                cy.login(studentOne, `/courses/${course.id}/messages?conversationId=${groupChat.id}`);
                courseMessages.addUserToGroupChatButton();
                courseMessages.addUserToGroupChat(studentTwo.username);
                courseMessages.updateGroupChat();

                courseMessages.listMembersButton(course.id!, groupChat.id!);
                courseMessages.checkMemberList(studentTwoName);
                courseMessages.closeEditPanel();
            });
        });

        describe('Leave group chat', () => {
            let groupChat: GroupChat;
            const groupChatName = 'leave-test';

            before('create group chat', () => {
                cy.login(admin);
                courseManagementRequest.createCourseMessageGroupChat(course, [studentOne.username, tutor.username]).then((response) => {
                    groupChat = response.body;
                    courseManagementRequest.updateCourseMessageGroupChatName(course, groupChat, groupChatName);
                });
            });

            it('tutor should be able to leave group chat', () => {
                cy.login(tutor, `/courses/${course.id}/messages?conversationId=${groupChat.id}`);
                courseMessages.checkGroupChatExists(groupChatName, true);
                courseMessages.listMembersButton(course.id!, groupChat.id!);
                courseMessages.openSettingsTab();
                courseMessages.leaveGroupChat();
                cy.visit(`/courses/${course.id}/messages`);
                courseMessages.checkGroupChatExists(groupChatName, false);
            });

            it('student should be able to leave group chat', () => {
                cy.login(studentOne, `/courses/${course.id}/messages?conversationId=${groupChat.id}`);
                courseMessages.checkGroupChatExists(groupChatName, true);
                courseMessages.listMembersButton(course.id!, groupChat.id!);
                courseMessages.openSettingsTab();
                courseMessages.leaveGroupChat();
                cy.visit(`/courses/${course.id}/messages`);
                courseMessages.checkGroupChatExists(groupChatName, false);
            });
        });

        describe('Write/edit/delete message in group chat', () => {
            let groupChat: GroupChat;
            before('create group chat', () => {
                cy.login(admin);
                courseManagementRequest.createCourseMessageGroupChat(course, [studentOne.username, tutor.username]).then((response) => {
                    groupChat = response.body;
                });
            });

            it('student should be able to write message in group chat', () => {
                cy.login(studentOne, `/courses/${course.id}/messages?conversationId=${groupChat.id}`);
                const messageText = 'Student Test Message';
                courseMessages.writeMessage(messageText);
                courseMessages.save().then((interception) => {
                    const message = interception.response!.body;
                    courseMessages.checkMessage(message.id, messageText);
                });
            });

            it('student should be able to edit message in group chat', () => {
                cy.login(studentOne, `/courses/${course.id}/messages?conversationId=${groupChat.id}`);
                const messageText = 'Student Edit Test Message';
                courseManagementRequest.createCourseMessage(course, groupChat.id!, 'groupChat', messageText).then((response) => {
                    const message = response.body;
                    const newMessage = 'Edited Text';
                    courseMessages.editMessage(message.id, newMessage);
                    courseMessages.checkMessage(message.id, newMessage);
                    courseMessages.getSinglePost(message.id).find('.edited-text').should('exist');
                });
            });

            it('student should be able to delete his message in group chat', () => {
                cy.login(studentOne, `/courses/${course.id}/messages?conversationId=${groupChat.id}`);
                const messageText = 'Student Edit Test Message';
                courseManagementRequest.createCourseMessage(course, groupChat.id!, 'groupChat', messageText).then((response) => {
                    const message = response.body;
                    courseMessages.checkMessage(message.id, messageText);
                    courseMessages.deleteMessage(message.id);
                    courseMessages.getSinglePost(message.id).should('not.exist');
                });
            });
        });
    });

    after('Delete course', () => {
        courseManagementRequest.deleteCourse(course, admin);
    });
});
