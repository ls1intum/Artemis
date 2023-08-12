import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { Channel } from 'app/entities/metis/conversation/channel.model';
import { GroupChat } from 'app/entities/metis/conversation/group-chat.model';
import { Post } from 'app/entities/metis/post.model';

import { COURSE_BASE, CourseWideContext, GET, POST, PUT } from '../constants';
import { CypressCredentials } from '../users';

/**
 * A class which encapsulates all API requests related to communications.
 */
export class CommunicationAPIRequests {
    /**
     * Creates a new course post.
     *
     * @param course - The course to which the post belongs.
     * @param title - The title of the post.
     * @param content - The content of the post.
     * @param context - The context of the course-wide post.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    createCoursePost(course: Course, title: string, content: string, context: CourseWideContext) {
        const body = {
            content,
            course: {
                id: course.id,
                title: course.title,
            },
            courseWideContext: context,
            displayPriority: 'NONE',
            title,
            tags: [],
            visibleForStudents: true,
        };
        return cy.request({ method: POST, url: `${COURSE_BASE}${course.id}/posts`, body });
    }

    /**
     * Creates a new course message channel.
     *
     * @param course - The course to which the message channel belongs.
     * @param name - The name of the message channel.
     * @param description - The description of the message channel.
     * @param isAnnouncementChannel - Set to true if the channel is an announcement channel.
     * @param isPublic - Set to true if the channel is public.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    createCourseMessageChannel(course: Course, name: string, description: string, isAnnouncementChannel: boolean, isPublic: boolean) {
        const body = {
            description,
            isAnnouncementChannel,
            isPublic,
            name,
            type: 'channel',
        };
        return cy.request({ method: POST, url: `${COURSE_BASE}${course.id}/channels`, body });
    }

    /**
     * Retrieves the exercise channel for a given course and exercise.
     *
     * @param courseId - The ID of the course.
     * @param exerciseId - The ID of the exercise.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    getExerciseChannel(courseId: number, exerciseId: number) {
        return cy.request({ method: GET, url: `${COURSE_BASE}${courseId}/exercises/${exerciseId}/channel` });
    }

    /**
     * Retrieves the lecture channel for a given course and lecture.
     *
     * @param courseId - The ID of the course.
     * @param lectureId - The ID of the lecture.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    getLectureChannel(courseId: number, exerciseId: number) {
        return cy.request({ method: GET, url: `${COURSE_BASE}${courseId}/lectures/${exerciseId}/channel` });
    }

    /**
     * Creates a new course message group chat.
     *
     * @param course - The course to which the group chat belongs.
     * @param users - An array of usernames of users to add to the group chat.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    createCourseMessageGroupChat(course: Course, users: Array<string>) {
        const body = users;
        return cy.request({ method: POST, url: `${COURSE_BASE}${course.id}/group-chats`, body });
    }

    /**
     * Creates a new course message.
     *
     * @param course - The course to which the message belongs.
     * @param targetId - The ID of the conversation target (channel or group chat).
     * @param type - The type of conversation target (e.g., 'channel' or 'groupChat').
     * @param message - The content of the message.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    createCourseMessage(course: Course, targetId: number, type: string, message: string) {
        const body = {
            content: message,
            conversation: {
                id: targetId,
                type,
            },
            displayPriority: 'NONE',
            visibleForStudents: true,
        };
        return cy.request({ method: POST, url: `${COURSE_BASE}${course.id}/messages`, body });
    }

    /**
     * Updates the name of a course message group chat.
     *
     * @param course - The course to which the group chat belongs.
     * @param groupChat - The group chat to update.
     * @param name - The new name of the group chat.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    updateCourseMessageGroupChatName(course: Course, groupChat: GroupChat, name: string) {
        const body = {
            name,
            type: 'groupChat',
        };
        return cy.request({ method: PUT, url: `${COURSE_BASE}${course.id}/group-chats/${groupChat.id}`, body });
    }

    /**
     * Joins a user into a channel.
     *
     * @param course - The course to which the channel belongs.
     * @param channel - The channel to join.
     * @param user - The user's credentials.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    joinUserIntoChannel(course: Course, channel: Channel, user: CypressCredentials) {
        const body = [user.username];
        return cy.request({ method: POST, url: `${COURSE_BASE}${course.id}/channels/${channel.id}/register`, body });
    }

    /**
     * Creates a new course post reply.
     *
     * @param course - The course to which the post belongs.
     * @param post - The post to which the reply is made.
     * @param content - The content of the post reply.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    createCoursePostReply(course: Course, post: Post, content: string) {
        const body = {
            content,
            post,
            resolvesPost: true,
        };
        return cy.request({ method: POST, url: `${COURSE_BASE}${course.id}/answer-posts`, body });
    }

    /**
     * Creates a new course exercise post.
     *
     * @param course - The course to which the post belongs.
     * @param exercise - The exercise to which the post is associated.
     * @param title - The title of the post.
     * @param content - The content of the post.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    createCourseExercisePost(course: Course, exercise: Exercise, title: string, content: string) {
        const body = {
            content,
            displayPriority: 'NONE',
            exercise: {
                id: exercise.id,
                title: exercise.title,
                type: exercise.type,
            },
            tags: [],
            title,
            visibleForStudents: true,
        };
        return cy.request({ method: POST, url: `${COURSE_BASE}${course.id}/posts`, body });
    }

    /**
     * Creates a new course lecture post.
     *
     * @param course - The course to which the post belongs.
     * @param lecture - The lecture to which the post is associated.
     * @param title - The title of the post.
     * @param content - The content of the post.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    createCourseLecturePost(course: Course, lecture: Lecture, title: string, content: string) {
        const body = {
            content,
            displayPriority: 'NONE',
            lecture: {
                id: lecture.id,
                title: lecture.title,
            },
            tags: [],
            title,
            visibleForStudents: true,
        };
        return cy.request({ method: POST, url: `${COURSE_BASE}${course.id}/posts`, body });
    }
}
