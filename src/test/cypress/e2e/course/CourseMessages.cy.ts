import { Channel } from 'app/entities/metis/conversation/channel.model';
import { Course } from '../../../../main/webapp/app/entities/course.model';
import { courseManagementRequest, courseMessages } from '../../support/artemis';
import { convertCourseAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import { admin, instructor, studentOne, studentTwo } from '../../support/users';
import { generateUUID } from '../../support/utils';

// Common primitives
let courseName: string;
let courseShortName: string;

describe('Course messages', () => {
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

    describe('Channel messages', () => {
        describe('Create channel', () => {
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

            it('instructors should not be able to create channel with name longer than 20 chars', () => {
                cy.login(instructor, `/courses/${course.id}/messages`);
                const name = 'way-to-long-channel-title';
                courseMessages.createChannelButton();
                courseMessages.setName(name);
                courseMessages.getError().contains('Name can be max 20 characters long!');
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
    });

    after('Delete Course', () => {
        cy.login(admin);
        if (courseId) {
            courseManagementRequest.deleteCourse(courseId).its('status').should('eq', 200);
        }
    });
});
