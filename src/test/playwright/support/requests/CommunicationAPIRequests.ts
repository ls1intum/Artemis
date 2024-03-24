import { Course } from 'app/entities/course.model';
import { Page } from '@playwright/test';
import { COURSE_BASE } from '../constants';
import { Channel, ChannelDTO, getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { GroupChat } from 'app/entities/metis/conversation/group-chat.model';
import { UserCredentials } from '../users';
import { Post } from 'app/entities/metis/post.model';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';

/**
 * A class which encapsulates all API requests related to communications.
 */
export class CommunicationAPIRequests {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Creates a new course post.
     *
     * @param course - The course to which the post belongs.
     * @param content - The content of the post.
     * @param channel - The channel the post belongs to
     * @returns Promise<Post> representing the course post created.
     */
    async createCoursePost(course: Course, content: string, channel: ChannelDTO): Promise<Post> {
        const data = {
            content,
            course: {
                id: course.id,
                title: course.title,
            },
            conversation: {
                id: channel.id,
                type: channel.type,
            },
            displayPriority: 'NONE',
            visibleForStudents: true,
        };
        const response = await this.page.request.post(`${COURSE_BASE}/${course.id}/messages`, { data });
        return response.json();
    }

    /**
     * Creates a new course message channel.
     *
     * @param course - The course to which the message channel belongs.
     * @param name - The name of the message channel.
     * @param description - The description of the message channel.
     * @param isAnnouncementChannel - Set to true if the channel is an announcement channel.
     * @param isPublic - Set to true if the channel is public.
     * @returns Promise<Channel> representing the created message channel.
     */
    async createCourseMessageChannel(course: Course, name: string, description: string, isAnnouncementChannel: boolean, isPublic: boolean): Promise<Channel> {
        const data = {
            description,
            isAnnouncementChannel,
            isPublic,
            name,
            type: 'channel',
        };
        const response = await this.page.request.post(`${COURSE_BASE}/${course.id}/channels`, { data });
        return response.json();
    }

    /**
     * Get course-wide channels of a course
     *
     * @param courseId - The id of the course
     * @returns Promise<ChannelDTO[]> with the course-wide channels of the course.
     */
    async getCourseWideChannels(courseId: number): Promise<ChannelDTO[]> {
        const response = await this.page.request.get(`${COURSE_BASE}/${courseId}/conversations`);
        const conversations: ConversationDTO[] = await response.json();
        return conversations.filter((conv: ConversationDTO) => getAsChannelDTO(conv)?.isCourseWide === true);
    }

    /**
     * Retrieves the exercise channel for a given course and exercise.
     *
     * @param courseId - The ID of the course.
     * @param exerciseId - The ID of the exercise.
     * @returns Promise<Channel> with the channel of the exercise.
     */
    async getExerciseChannel(courseId: number, exerciseId: number): Promise<Channel> {
        const response = await this.page.request.get(`${COURSE_BASE}/${courseId}/exercises/${exerciseId}/channel`);
        return response.json();
    }

    /**
     * Retrieves the lecture channel for a given course and lecture.
     *
     * @param courseId - The ID of the course.
     * @param lectureId - The ID of the lecture.
     * @returns Promise<Channel> with the channel of the lecture.
     */
    async getLectureChannel(courseId: number, lectureId: number): Promise<Channel> {
        const response = await this.page.request.get(`${COURSE_BASE}/${courseId}/lectures/${lectureId}/channel`);
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
        const response = await this.page.request.post(`${COURSE_BASE}/${course.id}/group-chats`, { data: users });
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
        const response = await this.page.request.post(`${COURSE_BASE}/${course.id}/messages`, { data });
        return response.json();
    }

    /**
     * Creates a new course message.
     *
     * @param course - The course to which the message belongs.
     * @param targetId - The ID of the conversation target channel.
     * @param message - The content of the message.
     * @returns Promise<Post> representing the message created.
     */
    async createCourseWideMessage(course: Course, targetId: number, message: string): Promise<Post> {
        return this.createCourseMessage(course, targetId, 'channel', message);
    }

    /**
     * Updates the name of a course message group chat.
     *
     * @param course - The course to which the group chat belongs.
     * @param groupChat - The group chat to update.
     * @param name - The new name of the group chat.
     */
    async updateCourseMessageGroupChatName(course: Course, groupChat: GroupChat, name: string) {
        const data = {
            name,
            type: 'groupChat',
        };
        await this.page.request.put(`${COURSE_BASE}/${course.id}/group-chats/${groupChat.id}`, { data });
    }

    /**
     * Joins a user into a channel.
     *
     * @param course - The course to which the channel belongs.
     * @param channelId - The id of the channel to join.
     * @param user - The user's credentials.
     */
    async joinUserIntoChannel(course: Course, channelId: number, user: UserCredentials) {
        const data = [user.username];
        await this.page.request.post(`${COURSE_BASE}/${course.id}/channels/${channelId}/register`, { data });
    }

    /**
     * Creates a new course post reply.
     *
     * @param course - The course to which the post belongs.
     * @param post - The post to which the reply is made.
     * @param content - The content of the post reply.
     * @returns Promise<Post> representing the message reply created.
     */
    async createCourseMessageReply(course: Course, post: Post, content: string): Promise<Post> {
        const data = {
            content,
            post,
            resolvesPost: true,
        };
        const response = await this.page.request.post(`${COURSE_BASE}/${course.id}/answer-messages`, { data });
        return response.json();
    }

    /**
     * Creates a new course exercise post.
     *
     * @param course - The course to which the post belongs.
     * @param exercise - The exercise to which the post is associated.
     * @param title - The title of the post.
     * @param content - The content of the post.
     * @returns Promise<Post> representing the course exercise post created.
     */
    async createCourseExercisePost(course: Course, exercise: Exercise, title: string, content: string) {
        const data = {
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
        const response = await this.page.request.post(`${COURSE_BASE}/${course.id}/posts`, { data });
        return response.json();
    }

    /**
     * Creates a new course lecture post.
     *
     * @param course - The course to which the post belongs.
     * @param lecture - The lecture to which the post is associated.
     * @param title - The title of the post.
     * @param content - The content of the post.
     * @returns Promise<Post> representing the course lecture post created.
     */
    async createCourseLecturePost(course: Course, lecture: Lecture, title: string, content: string) {
        const data = {
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
        const response = await this.page.request.post(`${COURSE_BASE}/${course.id}/posts`, { data });
        return response.json();
    }
}
