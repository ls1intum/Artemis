import { Course } from 'app/entities/course.model';
import { Page } from '@playwright/test';
import { COURSE_BASE } from '../constants';
import { Channel } from 'app/entities/metis/conversation/channel.model';
import { GroupChat } from 'app/entities/metis/conversation/group-chat.model';
import { UserCredentials } from '../users';
import { Post } from 'app/entities/metis/post.model';

/**
 * A class which encapsulates all API requests related to communications.
 */
export class CommunicationAPIRequests {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Creates a new course message channel.
     *
     * @param course - The course to which the message channel belongs.
     * @param name - The name of the message channel.
     * @param description - The description of the message channel.
     * @param isAnnouncementChannel - Set to true if the channel is an announcement channel.
     * @param isPublic - Set to true if the channel is public.
     * @returns Promise<ChannelDTO> representing the created message channel.
     */
    async createCourseMessageChannel(course: Course, name: string, description: string, isAnnouncementChannel: boolean, isPublic: boolean): Promise<Channel> {
        const data = {
            description,
            isAnnouncementChannel,
            isPublic,
            name,
            type: 'channel',
        };
        const response = await this.page.request.post(`${COURSE_BASE}${course.id}/channels`, { data });
        return response.json();
    }

    /**
     * Creates a new course message group chat.
     *
     * @param course - The course to which the group chat belongs.
     * @param users - An array of usernames of users to add to the group chat.
     * @returns Promise<GroupChat> representing the group chat created.
     */
    async createCourseMessageGroupChat(course: Course, users: Array<string>): Promise<GroupChat> {
        const response = await this.page.request.post(`${COURSE_BASE}${course.id}/group-chats`, { data: users });
        return response.json();
    }

    /**
     * Creates a new course message.
     *
     * @param course - The course to which the message belongs.
     * @param targetId - The ID of the conversation target (channel or group chat).
     * @param type - The type of conversation target (e.g., 'channel' or 'groupChat').
     * @param message - The content of the message.
     * @returns Promise<Post> representing the message created.
     */
    async createCourseMessage(course: Course, targetId: number, type: string, message: string): Promise<Post> {
        const data = {
            content: message,
            conversation: {
                id: targetId,
                type,
            },
            displayPriority: 'NONE',
            visibleForStudents: true,
        };
        const response = await this.page.request.post(`${COURSE_BASE}${course.id}/messages`, { data });
        return response.json();
    }

    /**
     * Updates the name of a course message group chat.
     *
     * @param course - The course to which the group chat belongs.
     * @param groupChat - The group chat to update.
     * @param name - The new name of the group chat.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    async updateCourseMessageGroupChatName(course: Course, groupChat: GroupChat, name: string) {
        const data = {
            name,
            type: 'groupChat',
        };
        await this.page.request.put(`${COURSE_BASE}${course.id}/group-chats/${groupChat.id}`, { data });
    }

    /**
     * Joins a user into a channel.
     *
     * @param course - The course to which the channel belongs.
     * @param channelId - The id of the channel to join.
     * @param user - The user's credentials.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    async joinUserIntoChannel(course: Course, channelId: number, user: UserCredentials) {
        const data = [user.username];
        await this.page.request.post(`${COURSE_BASE}${course.id}/channels/${channelId}/register`, { data });
    }
}
