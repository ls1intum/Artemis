import { Page, expect } from '@playwright/test';
import { BASE_API } from '../../constants';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { GroupChat } from 'app/entities/metis/conversation/group-chat.model';
import { Post } from 'app/entities/metis/post.model';

/**
 * A class which encapsulates UI selectors and actions for the course messages page.
 */
export class CourseMessagesPage {
    private page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async createChannelButton() {
        await this.page.locator('#channelButton').click();
        await this.page.locator('#createChannel').click();
    }

    async browseChannelsButton() {
        await this.page.locator('#channelButton').click();
        await this.page.locator('#channelOverview').click();
    }

    async browseExerciseChannelsButton() {
        await this.page.locator('#exerciseChannelButton').click();
        await this.page.locator('#exerciseChannelOverview').click();
    }

    async browseLectureChannelsButton() {
        await this.page.locator('#lectureChannelButton').click();
        await this.page.locator('#lectureChannelOverview').click();
    }

    async browseExamChannelsButton() {
        await this.page.locator('#examChannelButton').click();
        await this.page.locator('#examChannelOverview').click();
    }

    async checkChannelsExists(name: string) {
        await expect(this.page.locator('.channels-overview .list-group-item').getByText(name)).toBeVisible();
    }

    async getChannelIdByName(name: string) {
        const channelElement = this.page.locator('.channels-overview .list-group-item', { hasText: name });
        const id = await channelElement.getAttribute('id');
        return id?.replace('channel-', '');
    }

    async joinChannel(channelID: number) {
        const responsePromise = this.page.waitForResponse(`${BASE_API}courses/*/channels/*/register`);
        await this.page.locator(`#channel-${channelID} #register${channelID}`).click({ force: true });
        await responsePromise;
    }

    async leaveChannel(channelID: number) {
        const responsePromise = this.page.waitForResponse(`${BASE_API}courses/*/channels/*/deregister`);
        await this.page.locator(`#channel-${channelID} #deregister${channelID}`).click({ force: true });
        await responsePromise;
    }

    checkBadgeJoined(channelID: number) {
        return this.page.locator(`#channel-${channelID} .badge`);
    }

    async setName(name: string) {
        const locator = this.page.locator('.modal-content #name');
        await locator.clear();
        await locator.fill(name);
    }

    async setDescription(description: string) {
        const locator = this.page.locator('.modal-content #description');
        await locator.clear();
        await locator.fill(description);
    }

    async setPrivate() {
        await this.page.locator('.modal-content label[for="private"]').click();
    }

    async setPublic() {
        await this.page.locator('.modal-content label[for="public"]').click();
    }

    async setAnnouncementChannel() {
        await this.page.locator('.modal-content label[for="isAnnouncementChannel"]').click();
    }

    async setUnrestrictedChannel() {
        await this.page.locator('.modal-content label[for="isNotAnnouncementChannel"]').click();
    }

    async createChannel(isAnnouncementChannel: boolean, isPublic: boolean) {
        const responsePromise = this.page.waitForResponse(`${BASE_API}courses/*/channels`);
        await this.page.locator('.modal-content #submitButton').click();
        const response = await responsePromise;
        const channel: ChannelDTO = await response.json();
        await this.page.waitForURL(`**/messages?conversationId=${channel.id}`);
        expect(channel.isAnnouncementChannel).toBe(isAnnouncementChannel);
        expect(channel.isPublic).toBe(isPublic);
    }

    getError() {
        return this.page.locator('.modal-body .alert');
    }

    getName() {
        return this.page.locator('h3.conversation-name');
    }

    getTopic() {
        return this.page.locator('.conversation-topic');
    }

    async editName(newName: string) {
        await this.page.locator('#name-section .action-button').click();
        const nameField = this.page.locator('.channels-overview #name');
        await nameField.clear();
        await nameField.fill(newName);
        await this.page.locator('#submitButton').click();
    }

    async editTopic(newTopic: string) {
        await this.page.locator('#topic-section .action-button').click();
        const topicField = this.page.locator('.channels-overview #topic');
        await topicField.clear();
        await topicField.fill(newTopic);
        await this.page.locator('#submitButton').click();
    }

    async editDescription(newDescription: string) {
        await this.page.locator('#description-section .action-button').click();
        const descriptionField = this.page.locator('.channels-overview #description');
        await descriptionField.clear();
        await descriptionField.fill(newDescription);
        await this.page.locator('#submitButton').click();
    }

    async closeEditPanel() {
        await this.page.locator('.conversation-detail-dialog .btn-close').click();
    }

    async writeMessage(message: string) {
        const messageField = this.page.locator('.markdown-editor .ace_editor');
        await messageField.click();
        await messageField.pressSequentially(message);
    }

    async checkMessage(messageId: number, message: string) {
        const messagePreview = this.getSinglePost(messageId).locator('.markdown-preview').getByText(message);
        await expect(messagePreview).toBeVisible();
    }

    async editMessage(messageId: number, message: string) {
        const postLocator = this.getSinglePost(messageId);
        await postLocator.locator('.editIcon').click();
        const editorLocator = postLocator.locator('.markdown-editor .ace_editor');
        await editorLocator.click();
        await editorLocator.pressSequentially(message);
        const responsePromise = this.page.waitForResponse(`${BASE_API}courses/*/messages/*`);
        await postLocator.locator('#save').click();
        await responsePromise;
    }

    async deleteMessage(messageId: number) {
        const responsePromise = this.page.waitForResponse(`${BASE_API}courses/*/messages/*`);
        const deleteIcon = this.getSinglePost(messageId).locator('.deleteIcon');
        await deleteIcon.click();
        await deleteIcon.click();
        await responsePromise;
    }

    getSinglePost(postID: number) {
        return this.page.locator(`#item-${postID}`);
    }

    async save(force = false): Promise<Post> {
        const responsePromise = this.page.waitForResponse(`${BASE_API}courses/*/messages`);
        await this.page.locator('#save').click({ force });
        const response = await responsePromise;
        return response.json();
    }

    async createGroupChatButton() {
        await this.page.locator('#createGroupChat').click();
    }

    async createGroupChat(): Promise<GroupChat> {
        const responsePromise = this.page.waitForResponse(`${BASE_API}courses/*/group-chats`);
        await this.page.locator('#submitButton').click();
        const response = await responsePromise;
        return response.json();
    }

    async updateGroupChat() {
        const responsePromise = this.page.waitForResponse(`${BASE_API}courses/*/group-chats/*/register`);
        await this.page.locator('#submitButton').click();
        await responsePromise;
    }

    async addUserToGroupChat(user: string) {
        await this.page.locator('#users-selector0-user-input').fill(user);
        await this.page.locator('.dropdown-item', { hasText: `(${user})` }).click();
    }

    async addUserToGroupChatButton() {
        await this.page.locator('.addUsers').click();
    }

    async listMembersButton(courseID: number, conversationID: number) {
        await this.page.goto(`/courses/${courseID}/messages?conversationId=${conversationID}`);
        await this.page.locator('.members').click();
    }

    async checkMemberList(name: string) {
        await expect(this.page.locator('jhi-conversation-members')).toContainText(name);
    }

    async openSettingsTab() {
        await this.page.locator('.settings-tab').click();
    }

    async leaveGroupChat() {
        await this.page.locator('.leave-conversation').click();
    }

    async checkGroupChatExists(name: string, exist: boolean) {
        const groupChat = this.page.locator('.conversation-list').getByText(name);
        if (exist) {
            await expect(groupChat).toBeVisible();
        } else {
            await expect(groupChat).toBeHidden();
        }
    }

    async acceptCodeOfConductButton() {
        await this.page.locator('#acceptCodeOfConductButton').click();
    }
}
