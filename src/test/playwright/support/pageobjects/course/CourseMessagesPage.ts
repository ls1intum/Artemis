import { Page, expect } from '@playwright/test';
import { Channel, ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { GroupChat } from 'app/communication/shared/entities/conversation/group-chat.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { UserCredentials } from '../../users';
import { CommunicationAPIRequests } from '../../requests/CommunicationAPIRequests';
import { setMonacoEditorContent, setMonacoEditorContentByLocator } from '../../utils';

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
        await this.page.locator('.btn-primary.btn-sm.square-button').click();
        const createBtn = this.page.locator('button', { hasText: 'Create channel' });
        await createBtn.waitFor({ state: 'visible', timeout: 5000 });
        await createBtn.click();
    }

    /**
     * Navigates to the channel overview section.
     */
    async browseChannelsButton() {
        await this.page.locator('.btn-primary.btn-sm.square-button').click();
        const browseBtn = this.page.locator('button', { hasText: 'Browse channels' });
        await browseBtn.waitFor({ state: 'visible', timeout: 5000 });
        await browseBtn.click();
        // Wait for the channels overview to load
        await this.page.locator('.channels-overview').waitFor({ state: 'visible', timeout: 10000 });
    }

    /**
     * Checks if a channel exists by name.
     * @param name - The name of the channel to check for existence.
     */
    async checkChannelsExists(name: string) {
        await expect(this.page.locator('.channels-overview .list-group-item').getByText(name, { exact: true })).toBeVisible({ timeout: 15000 });
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
        const responsePromise = this.page.waitForResponse(`api/communication/courses/*/channels/*/register`);
        await this.page.locator(`#channel-${channelID} #register${channelID}`).click({ force: true });
        await responsePromise;
    }

    /**
     * Leaves a channel by its ID.
     * @param channelID - The ID of the channel to leave.
     */
    async leaveChannel(channelID: number) {
        const responsePromise = this.page.waitForResponse(`api/communication/courses/*/channels/*/deregister`);
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
        const locator = this.page.locator('.p-dialog-content #name');
        await locator.clear();
        await locator.fill(name);
    }

    /**
     * Sets the description in a modal dialog.
     * @param description - The description to be set.
     */
    async setDescription(description: string) {
        const locator = this.page.locator('.p-dialog-content #description');
        await locator.clear();
        await locator.fill(description);
    }

    /**
     * Sets a channel to be private in the modal dialog (PrimeNG SelectButton).
     */
    async setPrivate() {
        await this.page.locator('.p-dialog-content p-selectbutton').first().getByText('Private').click();
    }

    /**
     * Sets a channel to be public in the modal dialog (PrimeNG SelectButton).
     */
    async setPublic() {
        await this.page.locator('.p-dialog-content p-selectbutton').first().getByText('Public').click();
    }

    /**
     * Marks a channel as course-wide in the modal dialog (PrimeNG SelectButton).
     */
    async setCourseWideChannel() {
        await this.page.locator('.p-dialog-content p-selectbutton').nth(1).getByText('Course-wide Channel').click();
    }

    /**
     * Marks a channel as an announcement channel in the modal dialog (PrimeNG SelectButton).
     */
    async setAnnouncementChannel() {
        await this.page.locator('.p-dialog-content p-selectbutton').nth(2).getByText('Announcement Channel').click();
    }

    /**
     * Marks a channel as unrestricted in the modal dialog (PrimeNG SelectButton).
     */
    async setUnrestrictedChannel() {
        await this.page.locator('.p-dialog-content p-selectbutton').nth(2).getByText('Unrestricted Channel').click();
    }

    /**
     * Creates a channel with the specified properties.
     * @param isAnnouncementChannel - Specifies if the channel is an announcement channel.
     * @param isPublic - Specifies if the channel is public.
     */
    async createChannel(isAnnouncementChannel: boolean, isPublic: boolean) {
        const responsePromise = this.page.waitForResponse(
            (resp) => resp.url().includes('api/communication/courses/') && resp.url().endsWith('/channels') && resp.request().method() === 'POST' && resp.status() === 201,
        );
        await this.page.locator('.p-dialog-content #submitButton').click();
        const response = await responsePromise;
        const channel: ChannelDTO = await response.json();
        await this.page.waitForURL(`**/communication?conversationId=${channel.id}`);
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
        return this.page.locator('h4.d-inline-block');
    }

    /**
     * Retrieves the topic element from the conversation.
     * @returns The locator for the topic element.
     */
    getTopic() {
        return this.page.locator('#conversation-topic');
    }

    /**
     * Edits the name of the conversation inline with auto-save.
     * Opens the detail dialog via the header name click (which opens the Info tab).
     * @param newName - The new name for the conversation.
     */
    async editName(newName: string) {
        await this.getName().click();
        const nameInput = this.page.locator('#name-input');
        await nameInput.waitFor({ state: 'visible', timeout: 5000 });
        await nameInput.clear();
        // Register response listener before triggering the auto-save to avoid race conditions
        const responsePromise = this.page.waitForResponse((resp) => resp.url().includes('/channels/') && resp.request().method() === 'PUT');
        await nameInput.fill(newName);
        await responsePromise;
        await this.closeEditPanel();
    }

    /**
     * Edits the topic of the conversation inline with auto-save.
     * @param newTopic - The new topic for the conversation.
     */
    async editTopic(newTopic: string) {
        await this.getName().click();
        const topicInput = this.page.locator('#topic-input');
        await topicInput.waitFor({ state: 'visible', timeout: 5000 });
        await topicInput.clear();
        // Register response listener before triggering the auto-save to avoid race conditions
        const responsePromise = this.page.waitForResponse((resp) => resp.url().includes('/channels/') && resp.request().method() === 'PUT');
        await topicInput.fill(newTopic);
        await responsePromise;
        await this.closeEditPanel();
    }

    /**
     * Edits the description of the conversation inline with auto-save.
     * @param newDescription - The new description for the conversation.
     */
    async editDescription(newDescription: string) {
        await this.getName().click();
        const descInput = this.page.locator('#description-input');
        await descInput.waitFor({ state: 'visible', timeout: 5000 });
        await descInput.clear();
        // Register response listener before triggering the auto-save to avoid race conditions
        const responsePromise = this.page.waitForResponse((resp) => resp.url().includes('/channels/') && resp.request().method() === 'PUT');
        await descInput.fill(newDescription);
        await responsePromise;
        await this.closeEditPanel();
    }

    /**
     * Closes the edit panel in the conversation detail dialog.
     */
    async closeEditPanel() {
        await this.page.locator('.conversation-detail-dialog .btn-close').click();
    }

    /**
     * @param login method to log in a user and navigate to a certain URL
     * @param user who shall be logged in to create the communication channel
     * @param course in which the channel should be created
     * @param communicationAPIRequests to create the channel
     */
    async setupCommunicationChannel(
        login: (credentials: UserCredentials, url?: string) => Promise<void>,
        user: UserCredentials,
        course: Course,
        communicationAPIRequests: CommunicationAPIRequests,
    ): Promise<Channel> {
        await login(user, `/courses/${course.id}/communication`);
        await this.acceptCodeOfConductButton();
        const channel = await communicationAPIRequests.createCourseMessageChannel(course, 'test-channel', 'Test Channel', false, true);
        await communicationAPIRequests.joinUserIntoChannel(course, channel.id!, user);
        return channel;
    }

    /**
     * @param login method to log in a user and navigate to a certain URL
     * @param user who shall be logged in
     * @param courseId to which the channel belongs
     * @param channelId of the channel in which the message should be sent
     * @param messageText of the message to be sent
     *
     * @note The user will still be logged in after this method, make sure to log in with another user afterward if needed.
     */
    async sendMessageInChannel(
        login: (credentials: UserCredentials, url?: string) => Promise<void>,
        user: UserCredentials,
        courseId: number,
        channelId: number,
        messageText: string,
    ) {
        await login(user, `/courses/${courseId}/communication?conversationId=${channelId}`);
        await this.writeMessage(messageText);
        await this.save();
    }

    /**
     * Writes a message in the message field.
     * @param message - The message to be written.
     */
    async writeMessage(message: string) {
        // Use the specific posting markdown editor container
        await setMonacoEditorContent(this.page, 'jhi-posting-markdown-editor', message);
    }

    /**
     * Checks for the presence of a message by its ID and content.
     * @param messageId - The ID of the message to check.
     * @param message - The content of the message to verify.
     */
    async checkMessage(messageId: number, message: string) {
        const postElement = this.getSinglePost(messageId);
        await expect(postElement).toBeVisible({
            timeout: 30000,
        });

        const markdownPreview = postElement.locator('.markdown-preview');
        await expect(markdownPreview).toBeVisible({
            timeout: 30000,
        });

        const messagePreview = markdownPreview.getByText(message);
        await expect(messagePreview).toBeVisible({
            timeout: 30000,
        });
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
        await postLocator.scrollIntoViewIfNeeded();

        // Right-click to open context menu, with retry if the menu doesn't appear
        for (let attempt = 0; attempt < 3; attempt++) {
            await postLocator.locator('.message-container').click({ button: 'right' });
            try {
                await this.page.locator('.dropdown-menu.show').waitFor({ state: 'visible', timeout: 3000 });
                break;
            } catch {
                if (attempt === 2) throw new Error('Context menu did not appear after 3 right-click attempts');
            }
        }

        const editButton = postLocator.locator('.dropdown-menu.show .editIcon');
        if (await editButton.isVisible()) {
            await editButton.click();
        } else {
            await postLocator.locator('.reaction-button.edit').click();
        }

        // Use the setMonacoEditorContentByLocator utility to set the content directly
        await setMonacoEditorContentByLocator(this.page, postLocator, message);

        const responsePromise = this.page.waitForResponse(`api/communication/courses/*/messages/*`);
        await postLocator.locator('#save').click();
        await responsePromise;
    }

    /**
     * Deletes a message by its ID.
     * @param messageId - The ID of the message to delete.
     */
    async deleteMessage(messageId: number) {
        const postLocator = this.getSinglePost(messageId);
        await postLocator.scrollIntoViewIfNeeded();

        // Right-click to open context menu, with retry if the menu doesn't appear
        for (let attempt = 0; attempt < 3; attempt++) {
            await postLocator.locator('.message-container').click({ button: 'right' });
            try {
                await this.page.locator('.dropdown-menu.show').waitFor({ state: 'visible', timeout: 3000 });
                break;
            } catch {
                if (attempt === 2) throw new Error('Context menu did not appear after 3 right-click attempts');
            }
        }

        const responsePromise = this.page.waitForResponse(`api/communication/courses/*/messages/*`);
        const deleteButton = postLocator.locator('.dropdown-menu.show .deleteIcon');
        if (await deleteButton.isVisible()) {
            await deleteButton.click();
        } else {
            await postLocator.locator('.reaction-button.delete').click();
        }
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
    async save(): Promise<Post> {
        const responsePromise = this.page.waitForResponse(
            (resp) => resp.url().includes('api/communication/courses/') && resp.url().endsWith('/messages') && resp.request().method() === 'POST',
        );
        const saveButton = this.page.locator('#save');
        await expect(saveButton).toBeEnabled({ timeout: 10000 });
        await saveButton.click({ timeout: 10000 });
        const response = await responsePromise;
        expect(response.status()).toBe(201);
        return response.json();
    }

    /**
     * Clicks the button to initiate group chat creation.
     */
    async createGroupChatButton() {
        await this.page.locator('.btn-primary.btn-sm.square-button').click();
        const createBtn = this.page.locator('button', { hasText: 'Create group chat' });
        await createBtn.waitFor({ state: 'visible', timeout: 5000 });
        await createBtn.click();
    }

    /**
     * Creates a group chat and waits for the response.
     * @returns A promise that resolves with the GroupChat object after creation.
     */
    async createGroupChat(): Promise<GroupChat> {
        const responsePromise = this.page.waitForResponse((resp) => resp.url().includes('/group-chats') && !resp.url().includes('/register') && resp.request().method() === 'POST');
        await this.page.locator('#submitButton').click();
        const response = await responsePromise;
        const groupChat: GroupChat = await response.json();
        // Wait for Angular to navigate to the new conversation
        await this.page.waitForURL(`**/communication?conversationId=${groupChat.id}`, { timeout: 10000 });
        return groupChat;
    }

    /**
     * Updates a group chat's registration status and waits for the response.
     */
    async updateGroupChat() {
        const submitButton = this.page.locator('#submitButton');
        await expect(submitButton).toBeEnabled({ timeout: 10000 });
        const responsePromise = this.page.waitForResponse((resp) => resp.url().includes('/group-chats/') && resp.url().includes('/register') && resp.request().method() === 'POST');
        await submitButton.click();
        const response = await responsePromise;
        expect(response.status()).toBeLessThan(300);
        // Wait for Angular to process the response and update the conversation header
        await this.page.waitForTimeout(500);
    }

    /**
     * Adds a user to a group chat by username.
     * @param user - The username of the user to add to the group chat.
     */
    async addUserToGroupChat(user: string) {
        // Use a flexible selector that matches any users-selector search input (the ID suffix is a global counter)
        const searchInput = this.page.locator('input[id$="-search-input"][id^="users-selector"]');
        const dropdownItem = this.page.locator('.dropdown-item', { hasText: `(${user})` });
        for (let attempt = 0; attempt < 3; attempt++) {
            await searchInput.clear();
            await searchInput.fill(user);
            try {
                await dropdownItem.waitFor({ state: 'visible', timeout: 8000 });
                await dropdownItem.click();
                return;
            } catch {
                if (attempt === 2) throw new Error(`User search dropdown did not appear after 3 attempts for '${user}'`);
            }
        }
    }

    /**
     * Clicks the button to add users to a group chat and waits for the modal to appear.
     */
    async addUserToGroupChatButton() {
        const addUsersBtn = this.page.locator('.addUsers');
        await addUsersBtn.waitFor({ state: 'visible', timeout: 10000 });
        await addUsersBtn.click();
        // Wait for the add-users modal form to render (use flexible selector for the search input ID)
        await this.page.locator('input[id$="-search-input"][id^="users-selector"]').waitFor({ state: 'visible', timeout: 10000 });
    }

    /**
     * Navigates to a conversation and waits for it to become active.
     * Angular's setActiveConversation() looks up the conversationId in a cached list.
     * If the cache isn't ready when the route first processes, the conversation won't activate.
     * We handle this by navigating to the full URL, waiting for the conversations API to respond
     * (which populates the cache), then reloading if the conversation didn't activate.
     */
    async openConversation(courseID: number, conversationID: number) {
        const fullUrl = `/courses/${courseID}/communication?conversationId=${conversationID}`;
        const membersButton = this.page.locator('.members');

        // Attempt 1: Navigate directly to the conversation URL
        // Set up the response waiter BEFORE navigation so we don't miss the response
        const conversationsApiPromise = this.page.waitForResponse(
            (resp) =>
                resp.url().includes('/api/communication/courses/') &&
                resp.url().match(/\/conversations(\?|$)/) !== null &&
                resp.request().method() === 'GET' &&
                resp.status() === 200,
        );
        await this.page.goto(fullUrl);
        // Wait for the conversations list API to complete (cache population)
        await conversationsApiPromise.catch(() => {});

        // Check if the conversation became active
        try {
            await membersButton.waitFor({ state: 'visible', timeout: 10000 });
            return;
        } catch {
            // Conversation didn't activate — cache may not have been ready in time
        }

        // Attempt 2: Reload the page so Angular re-processes the route with a populated cache
        await this.page.reload({ waitUntil: 'domcontentloaded' });
        try {
            await membersButton.waitFor({ state: 'visible', timeout: 10000 });
            return;
        } catch {
            // Still not active
        }

        // Attempt 3: Full fresh navigation with wait for API
        const apiPromise2 = this.page.waitForResponse(
            (resp) =>
                resp.url().includes('/api/communication/courses/') &&
                resp.url().match(/\/conversations(\?|$)/) !== null &&
                resp.request().method() === 'GET' &&
                resp.status() === 200,
        );
        await this.page.goto(fullUrl);
        await apiPromise2.catch(() => {});
        await membersButton.waitFor({ state: 'visible', timeout: 10000 });
    }

    async listMembersButton(courseID: number, conversationID: number) {
        const membersButton = this.page.locator('.members');
        try {
            await membersButton.waitFor({ state: 'visible', timeout: 10000 });
        } catch {
            await this.openConversation(courseID, conversationID);
        }
        await membersButton.click();
        // Wait for the members dialog to render
        await this.page.locator('jhi-conversation-members').waitFor({ state: 'visible', timeout: 10000 });
    }

    /**
     * Checks for the presence of a member in the member list by name.
     * @param name - The name of the member to check for in the list.
     */
    async checkMemberList(name: string) {
        await expect(this.page.locator('jhi-conversation-members')).toContainText(name, { timeout: 15000 });
    }

    /**
     * Checks that the conversation header (h4 title) contains the given name.
     * This is faster and more reliable than opening the member list dialog.
     */
    async checkConversationHeaderContains(name: string) {
        await expect(this.page.locator('h4.d-inline-block')).toContainText(name, { timeout: 10000 });
    }

    /**
     * Opens the settings tab within the conversation details.
     */
    async openSettingsTab() {
        const settingsTab = this.page.locator('.settings-tab .nav-link');
        await settingsTab.waitFor({ state: 'visible', timeout: 10000 });
        await settingsTab.click();
    }

    /**
     * Leaves the current group chat.
     */
    async leaveGroupChat() {
        const leaveButton = this.page.locator('.leave-conversation');
        await leaveButton.waitFor({ state: 'visible', timeout: 10000 });
        const responsePromise = this.page.waitForResponse((resp) => resp.url().includes('/group-chats/') && resp.url().includes('/deregister') && resp.status() === 200);
        await leaveButton.click();
        await responsePromise;
    }

    /**
     * Checks if a group chat with the specified name exists or not.
     * @param name - The name of the group chat to check for existence.
     * @param exist - A boolean indicating whether the group chat should exist or not.
     */
    async checkGroupChatExists(name: string, exist: boolean) {
        const groupChat = this.page.getByTitle(name);
        if (exist) {
            await expect(groupChat).toBeVisible({ timeout: 15000 });
        } else {
            await expect(groupChat).toBeHidden({ timeout: 15000 });
        }
    }

    /**
     * Right-clicks a message to open context menu, then clicks the bookmark button.
     * @param messageId - The ID of the message to bookmark.
     */
    async bookmarkMessage(messageId: number) {
        const postLocator = this.getSinglePost(messageId);
        await postLocator.scrollIntoViewIfNeeded();

        // Right-click to open context menu with bookmark option
        for (let attempt = 0; attempt < 3; attempt++) {
            await postLocator.locator('.message-container').click({ button: 'right' });
            try {
                await this.page.locator('.dropdown-menu.show').waitFor({ state: 'visible', timeout: 3000 });
                break;
            } catch {
                if (attempt === 2) throw new Error('Context menu did not appear after 3 right-click attempts');
            }
        }

        const responsePromise = this.page.waitForResponse(
            (resp) => resp.url().includes('/saved-posts') && (resp.request().method() === 'POST' || resp.request().method() === 'DELETE'),
        );
        await postLocator.locator('.dropdown-menu.show .dropdown-item', { hasText: /bookmark|save/i }).click();
        await responsePromise;
    }

    /**
     * Checks whether a message is bookmarked or not.
     * @param messageId - The ID of the message to check.
     * @param isBookmarked - Whether the message should be bookmarked.
     */
    async checkMessageBookmarked(messageId: number, isBookmarked: boolean) {
        const postLocator = this.getSinglePost(messageId);
        if (isBookmarked) {
            await expect(postLocator.locator('.is-saved').first()).toBeVisible({ timeout: 10000 });
        } else {
            await expect(postLocator.locator('.is-saved')).toHaveCount(0, { timeout: 10000 });
        }
    }

    /**
     * Adds an emoji reaction to a message via the reaction bar.
     * @param messageId - The ID of the message to react to.
     * @param emoji - The emoji search term (e.g., 'thumbsup').
     */
    async addReactionToMessage(messageId: number, emoji: string) {
        const postLocator = this.getSinglePost(messageId);
        await postLocator.scrollIntoViewIfNeeded();

        // Right-click to open context menu which has "Add reaction"
        for (let attempt = 0; attempt < 3; attempt++) {
            await postLocator.locator('.message-container').click({ button: 'right' });
            try {
                await this.page.locator('.dropdown-menu.show').waitFor({ state: 'visible', timeout: 3000 });
                break;
            } catch {
                if (attempt === 2) throw new Error('Context menu did not appear after 3 right-click attempts');
            }
        }

        // Click the "Add reaction" item — this opens the emoji picker at the click location
        await this.page.locator('.dropdown-menu.show .dropdown-item', { hasText: /reaction/i }).click();
        // Search for the emoji and click it
        await this.page.locator('.emoji-mart').waitFor({ state: 'visible', timeout: 5000 });
        await this.page.locator('.emoji-mart').locator('.emoji-mart-search input').fill(emoji);
        const responsePromise = this.page.waitForResponse((resp) => resp.url().includes('/postings/reactions') && resp.request().method() === 'POST');
        await this.page.locator('.emoji-mart').locator('.emoji-mart-scroll').locator('ngx-emoji').first().click();
        await responsePromise;
    }

    /**
     * Checks that a reaction badge exists on a message.
     * @param messageId - The ID of the message to check.
     */
    async checkReactionOnMessage(messageId: number) {
        const postLocator = this.getSinglePost(messageId);
        // Reactions are displayed inside .emoji-container within the emoji-count reaction bar
        await expect(postLocator.locator('.emoji-container').first()).toBeVisible({ timeout: 10000 });
    }

    /**
     * Opens the thread sidebar for a message by clicking the reply button.
     * @param messageId - The ID of the message to open thread for.
     */
    async openThreadForMessage(messageId: number) {
        const postLocator = this.getSinglePost(messageId);
        await postLocator.scrollIntoViewIfNeeded();

        // Right-click to open context menu with reply option
        for (let attempt = 0; attempt < 3; attempt++) {
            await postLocator.locator('.message-container').click({ button: 'right' });
            try {
                await this.page.locator('.dropdown-menu.show').waitFor({ state: 'visible', timeout: 3000 });
                break;
            } catch {
                if (attempt === 2) throw new Error('Context menu did not appear after 3 right-click attempts');
            }
        }

        await postLocator.locator('.dropdown-menu.show .dropdown-item', { hasText: /reply/i }).click();
        // Wait for the thread sidebar to become visible
        await this.page.locator('.expanded-thread').waitFor({ state: 'visible', timeout: 10000 });
    }

    /**
     * Types a reply in the thread sidebar and saves it.
     * @param content - The reply content.
     * @returns Promise<Post> representing the created reply.
     */
    async replyInThread(content: string): Promise<Post> {
        const threadSidebar = this.page.locator('.expanded-thread');
        const replyContainer = threadSidebar.locator('jhi-message-reply-inline-input jhi-posting-markdown-editor');
        await setMonacoEditorContentByLocator(this.page, replyContainer, content);
        const responsePromise = this.page.waitForResponse((resp) => resp.url().includes('/answer-messages') && resp.request().method() === 'POST');
        await threadSidebar.locator('jhi-message-reply-inline-input #save').click();
        const response = await responsePromise;
        return response.json();
    }

    /**
     * Checks that a reply is visible in the thread sidebar.
     * @param replyId - The ID of the reply to check.
     * @param content - The expected content of the reply.
     */
    async checkThreadReply(replyId: number, content: string) {
        const replyLocator = this.page.locator(`.expanded-thread #item-${replyId}`);
        await expect(replyLocator).toBeVisible({ timeout: 10000 });
        await expect(replyLocator.locator('.markdown-preview')).toContainText(content);
    }

    /**
     * Clicks the "+" button and selects "Create direct message".
     */
    async createDirectMessageButton() {
        await this.page.locator('.btn-primary.btn-sm.square-button').click();
        const createBtn = this.page.locator('button', { hasText: 'Direct message' });
        await createBtn.waitFor({ state: 'visible', timeout: 5000 });
        await createBtn.click();
    }

    /**
     * Forwards a message via the context menu.
     * @param messageId - The ID of the message to forward.
     */
    async forwardMessage(messageId: number) {
        const postLocator = this.getSinglePost(messageId);
        await postLocator.scrollIntoViewIfNeeded();

        // Right-click to open context menu with forward option
        for (let attempt = 0; attempt < 3; attempt++) {
            await postLocator.locator('.message-container').click({ button: 'right' });
            try {
                await this.page.locator('.dropdown-menu.show').waitFor({ state: 'visible', timeout: 3000 });
                break;
            } catch {
                if (attempt === 2) throw new Error('Context menu did not appear after 3 right-click attempts');
            }
        }

        await postLocator.locator('.dropdown-menu.show .forward').click();
        // Wait for the forward dialog to appear
        await this.page.locator('jhi-forward-message-dialog').waitFor({ state: 'visible', timeout: 10000 });
    }

    /**
     * Pins or unpins a message via the context menu.
     * @param messageId - The ID of the message to pin/unpin.
     */
    async pinMessage(messageId: number) {
        const postLocator = this.getSinglePost(messageId);
        await postLocator.scrollIntoViewIfNeeded();

        for (let attempt = 0; attempt < 3; attempt++) {
            await postLocator.locator('.message-container').click({ button: 'right' });
            try {
                await this.page.locator('.dropdown-menu.show').waitFor({ state: 'visible', timeout: 3000 });
                break;
            } catch {
                if (attempt === 2) throw new Error('Context menu did not appear after 3 right-click attempts');
            }
        }

        const responsePromise = this.page.waitForResponse((resp) => resp.url().includes('/messages/') && resp.request().method() === 'PUT');
        await postLocator.locator('.dropdown-menu.show .dropdown-item', { hasText: /pin/i }).click();
        await responsePromise;
    }

    /**
     * Checks whether a message is pinned.
     * @param messageId - The ID of the message to check.
     * @param isPinned - Whether the message should be pinned.
     */
    async checkMessagePinned(messageId: number, isPinned: boolean) {
        const postLocator = this.getSinglePost(messageId);
        if (isPinned) {
            await expect(postLocator.locator('.pinned-message, .pin-icon')).toBeVisible({ timeout: 10000 });
        } else {
            await expect(postLocator.locator('.pinned-message')).toHaveCount(0, { timeout: 10000 });
        }
    }

    /**
     * Edits a reply in the thread sidebar.
     * @param replyId - The ID of the reply to edit.
     * @param newContent - The new content for the reply.
     */
    async editThreadReply(replyId: number, newContent: string) {
        const threadSidebar = this.page.locator('.expanded-thread');
        const replyLocator = threadSidebar.locator(`#item-${replyId}`);
        await replyLocator.scrollIntoViewIfNeeded();

        for (let attempt = 0; attempt < 3; attempt++) {
            await replyLocator.locator('.message-container').click({ button: 'right' });
            try {
                await this.page.locator('.dropdown-menu.show').waitFor({ state: 'visible', timeout: 3000 });
                break;
            } catch {
                if (attempt === 2) throw new Error('Context menu did not appear after 3 right-click attempts');
            }
        }

        // Click "Edit Message" from the dropdown by matching translated text
        await this.page.locator('.dropdown-menu.show .dropdown-item', { hasText: /edit/i }).click();

        await setMonacoEditorContentByLocator(this.page, replyLocator, newContent);
        const responsePromise = this.page.waitForResponse((resp) => resp.url().includes('/answer-messages/') && resp.request().method() === 'PUT');
        await replyLocator.locator('#save').click();
        await responsePromise;
    }

    /**
     * Deletes a reply in the thread sidebar.
     * @param replyId - The ID of the reply to delete.
     */
    async deleteThreadReply(replyId: number) {
        const threadSidebar = this.page.locator('.expanded-thread');
        const replyLocator = threadSidebar.locator(`#item-${replyId}`);
        await replyLocator.scrollIntoViewIfNeeded();

        for (let attempt = 0; attempt < 3; attempt++) {
            await replyLocator.locator('.message-container').click({ button: 'right' });
            try {
                await this.page.locator('.dropdown-menu.show').waitFor({ state: 'visible', timeout: 3000 });
                break;
            } catch {
                if (attempt === 2) throw new Error('Context menu did not appear after 3 right-click attempts');
            }
        }

        const responsePromise = this.page.waitForResponse((resp) => resp.url().includes('/answer-messages/') && resp.request().method() === 'DELETE');
        // Click "Delete Message" from the dropdown by matching translated text
        await this.page.locator('.dropdown-menu.show .dropdown-item', { hasText: /delete/i }).click();
        await responsePromise;
    }

    toggleSidebarAccordion(sidebarTitle: string) {
        return this.page.locator(`#test-accordion-item-header-${sidebarTitle}`);
    }

    /**
     * Accepts the code of conduct by clicking the respective button if it's visible.
     * If the user has already accepted the code of conduct, the button won't be present.
     */
    async acceptCodeOfConductButton() {
        const button = this.page.locator('#acceptCodeOfConductButton');
        // Wait a short time for the page to load and determine if the button should be shown
        await this.page.waitForLoadState('domcontentloaded');
        if (await button.isVisible()) {
            await button.click();
            // Wait for the acceptance to be processed
            await this.page.waitForLoadState('domcontentloaded');
        }
    }
}
