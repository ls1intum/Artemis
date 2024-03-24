import { Page, expect } from '@playwright/test';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { GroupChat } from 'app/entities/metis/conversation/group-chat.model';
import { Post } from 'app/entities/metis/post.model';
import { COURSE_BASE } from '../../constants';

/**
 * A class which encapsulates UI selectors and actions for the Course Messages page.
 */
export class CourseMessagesPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Clicks the button to initiate channel creation.
     */
    async createChannelButton() {
        await this.page.locator('#channelButton').click();
        await this.page.locator('#createChannel').click();
    }

    /**
     * Navigates to the channel overview section.
     */
    async browseChannelsButton() {
        await this.page.locator('#channelButton').click();
        await this.page.locator('#channelOverview').click();
    }

    /**
     * Navigates to the exercise channel overview section.
     */
    async browseExerciseChannelsButton() {
        await this.page.locator('#exerciseChannelButton').click();
        await this.page.locator('#exerciseChannelOverview').click();
    }

    /**
     * Navigates to the lecture channel overview section.
     */
    async browseLectureChannelsButton() {
        await this.page.locator('#lectureChannelButton').click();
        await this.page.locator('#lectureChannelOverview').click();
    }

    /**
     * Navigates to the exam channel overview section.
     */
    async browseExamChannelsButton() {
        await this.page.locator('#examChannelButton').click();
        await this.page.locator('#examChannelOverview').click();
    }

    /**
     * Checks if a channel exists by name.
     * @param name - The name of the channel to check for existence.
     */
    async checkChannelsExists(name: string) {
        await expect(this.page.locator('.channels-overview .list-group-item').getByText(name)).toBeVisible();
    }

    /**
     * Retrieves the ID of a channel by its name.
     * @param name - The name of the channel whose ID is to be retrieved.
     * @returns The ID of the channel.
     */
    async getChannelIdByName(name: string) {
        const channelElement = this.page.locator('.channels-overview .list-group-item', { hasText: name });
        const id = await channelElement.getAttribute('id');
        return id?.replace('channel-', '');
    }

    /**
     * Joins a channel by its ID.
     * @param channelID - The ID of the channel to join.
     */
    async joinChannel(channelID: number) {
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/channels/*/register`);
        await this.page.locator(`#channel-${channelID} #register${channelID}`).click({ force: true });
        await responsePromise;
    }

    /**
     * Leaves a channel by its ID.
     * @param channelID - The ID of the channel to leave.
     */
    async leaveChannel(channelID: number) {
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/channels/*/deregister`);
        await this.page.locator(`#channel-${channelID} #deregister${channelID}`).click({ force: true });
        await responsePromise;
    }

    /**
     * Gets the 'joined' badge of a channel.
     * @param channelID - The ID of the channel.
     * @returns The locator for the badge element.
     */
    getJoinedBadge(channelID: number) {
        return this.page.locator(`#channel-${channelID} .badge`);
    }

    /**
     * Sets the name in a modal dialog.
     * @param name - The name to be set.
     */
    async setName(name: string) {
        const locator = this.page.locator('.modal-content #name');
        await locator.clear();
        await locator.fill(name);
    }

    /**
     * Sets the description in a modal dialog.
     * @param description - The description to be set.
     */
    async setDescription(description: string) {
        const locator = this.page.locator('.modal-content #description');
        await locator.clear();
        await locator.fill(description);
    }

    /**
     * Sets a channel to be private in the modal dialog.
     */
    async setPrivate() {
        await this.page.locator('.modal-content label[for="private"]').click();
    }

    /**
     * Sets a channel to be public in the modal dialog.
     */
    async setPublic() {
        await this.page.locator('.modal-content label[for="public"]').click();
    }

    /**
     * Marks a channel as an announcement channel in the modal dialog.
     */
    async setAnnouncementChannel() {
        await this.page.locator('.modal-content label[for="isAnnouncementChannel"]').click();
    }

    /**
     * Marks a channel as unrestricted in the modal dialog.
     */
    async setUnrestrictedChannel() {
        await this.page.locator('.modal-content label[for="isNotAnnouncementChannel"]').click();
    }

    /**
     * Creates a channel with the specified properties.
     * @param isAnnouncementChannel - Specifies if the channel is an announcement channel.
     * @param isPublic - Specifies if the channel is public.
     */
    async createChannel(isAnnouncementChannel: boolean, isPublic: boolean) {
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/channels`);
        await this.page.locator('.modal-content #submitButton').click();
        const response = await responsePromise;
        const channel: ChannelDTO = await response.json();
        await this.page.waitForURL(`**/messages?conversationId=${channel.id}`);
        expect(channel.isAnnouncementChannel).toBe(isAnnouncementChannel);
        expect(channel.isPublic).toBe(isPublic);
    }

    /**
     * Retrieves the error message element from the modal dialog.
     * @returns The locator for the error message element.
     */
    getError() {
        return this.page.locator('.modal-body .alert');
    }

    /**
     * Retrieves the name element from the conversation.
     * @returns The locator for the name element.
     */
    getName() {
        return this.page.locator('h3.conversation-name');
    }

    /**
     * Retrieves the topic element from the conversation.
     * @returns The locator for the topic element.
     */
    getTopic() {
        return this.page.locator('.conversation-topic');
    }

    /**
     * Edits the name of the conversation to a new name.
     * @param newName - The new name for the conversation.
     */
    async editName(newName: string) {
        await this.page.locator('#name-section .action-button').click();
        const nameField = this.page.locator('.channels-overview #name');
        await nameField.clear();
        await nameField.fill(newName);
        await this.page.locator('#submitButton').click();
    }

    /**
     * Edits the topic of the conversation to a new topic.
     * @param newTopic - The new topic for the conversation.
     */
    async editTopic(newTopic: string) {
        await this.page.locator('#topic-section .action-button').click();
        const topicField = this.page.locator('.channels-overview #topic');
        await topicField.clear();
        await topicField.fill(newTopic);
        await this.page.locator('#submitButton').click();
    }

    /**
     * Edits the description of the conversation to a new description.
     * @param newDescription - The new description for the conversation.
     */
    async editDescription(newDescription: string) {
        await this.page.locator('#description-section .action-button').click();
        const descriptionField = this.page.locator('.channels-overview #description');
        await descriptionField.clear();
        await descriptionField.fill(newDescription);
        await this.page.locator('#submitButton').click();
    }

    /**
     * Closes the edit panel in the conversation detail dialog.
     */
    async closeEditPanel() {
        await this.page.locator('.conversation-detail-dialog .btn-close').click();
    }

    /**
     * Writes a message in the message field.
     * @param message - The message to be written.
     */
    async writeMessage(message: string) {
        const messageField = this.page.locator('.markdown-editor .ace_editor');
        await messageField.click();
        await messageField.pressSequentially(message);
    }

    /**
     * Checks for the presence of a message by its ID and content.
     * @param messageId - The ID of the message to check.
     * @param message - The content of the message to verify.
     */
    async checkMessage(messageId: number, message: string) {
        const messagePreview = this.getSinglePost(messageId).locator('.markdown-preview').getByText(message);
        await expect(messagePreview).toBeVisible();
    }

    /**
     * Verifies that an edited message is marked with "edited" notice.
     * @param messageId - The ID of the message to check.
     */
    async checkMessageEdited(messageId: number) {
        await expect(this.getSinglePost(messageId).locator('.edited-text', { hasText: '(edited)' })).toBeVisible();
    }

    /**
     * Edits a message by its ID to have new content.
     * @param messageId - The ID of the message to edit.
     * @param message - The new content for the message.
     */
    async editMessage(messageId: number, message: string) {
        const postLocator = this.getSinglePost(messageId);
        await postLocator.locator('.editIcon').click();
        const editorLocator = postLocator.locator('.markdown-editor .ace_editor');
        await editorLocator.click();
        await editorLocator.pressSequentially(message);
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/messages/*`);
        await postLocator.locator('#save').click();
        await responsePromise;
    }

    /**
     * Deletes a message by its ID.
     * @param messageId - The ID of the message to delete.
     */
    async deleteMessage(messageId: number) {
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/messages/*`);
        const deleteIcon = this.getSinglePost(messageId).locator('.deleteIcon');
        await deleteIcon.click();
        await deleteIcon.click();
        await responsePromise;
    }

    /**
     * Retrieves a single post by its ID.
     * @param postID - The ID of the post to retrieve.
     * @returns The locator for the specified post.
     */
    getSinglePost(postID: number) {
        return this.page.locator(`#item-${postID}`);
    }

    /**
     * Saves changes made in a modal dialog, optionally forcing the click action.
     * @param force - Whether to force the click action.
     * @returns A promise that resolves with the Post object after saving.
     */
    async save(force = false): Promise<Post> {
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/messages`);
        await this.page.locator('#save').click({ force });
        const response = await responsePromise;
        return response.json();
    }

    /**
     * Clicks the button to initiate group chat creation.
     */
    async createGroupChatButton() {
        await this.page.locator('#createGroupChat').click();
    }

    /**
     * Creates a group chat and waits for the response.
     * @returns A promise that resolves with the GroupChat object after creation.
     */
    async createGroupChat(): Promise<GroupChat> {
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/group-chats`);
        await this.page.locator('#submitButton').click();
        const response = await responsePromise;
        return response.json();
    }

    /**
     * Updates a group chat's registration status and waits for the response.
     */
    async updateGroupChat() {
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/group-chats/*/register`);
        await this.page.locator('#submitButton').click();
        await responsePromise;
    }

    /**
     * Adds a user to a group chat by username.
     * @param user - The username of the user to add to the group chat.
     */
    async addUserToGroupChat(user: string) {
        await this.page.locator('#users-selector0-user-input').fill(user);
        await this.page.locator('.dropdown-item', { hasText: `(${user})` }).click();
    }

    /**
     * Clicks the button to add users to a group chat.
     */
    async addUserToGroupChatButton() {
        await this.page.locator('.addUsers').click();
    }

    /**
     * Navigates to a specific conversation in a course and opens the member list.
     * @param courseID - The ID of the course.
     * @param conversationID - The ID of the conversation.
     */
    async listMembersButton(courseID: number, conversationID: number) {
        await this.page.goto(`/courses/${courseID}/messages?conversationId=${conversationID}`);
        await this.page.locator('.members').click();
    }

    /**
     * Checks for the presence of a member in the member list by name.
     * @param name - The name of the member to check for in the list.
     */
    async checkMemberList(name: string) {
        await expect(this.page.locator('jhi-conversation-members')).toContainText(name);
    }

    /**
     * Opens the settings tab within the conversation details.
     */
    async openSettingsTab() {
        await this.page.locator('.settings-tab').click();
    }

    /**
     * Leaves the current group chat.
     */
    async leaveGroupChat() {
        await this.page.locator('.leave-conversation').click();
    }

    /**
     * Checks if a group chat with the specified name exists or not.
     * @param name - The name of the group chat to check for existence.
     * @param exist - A boolean indicating whether the group chat should exist or not.
     */
    async checkGroupChatExists(name: string, exist: boolean) {
        const groupChat = this.page.locator('.conversation-list').getByText(name);
        if (exist) {
            await expect(groupChat).toBeVisible();
        } else {
            await expect(groupChat).toBeHidden();
        }
    }

    /**
     * Accepts the code of conduct by clicking the respective button.
     */
    async acceptCodeOfConductButton() {
        await this.page.locator('#acceptCodeOfConductButton').click();
    }
}
