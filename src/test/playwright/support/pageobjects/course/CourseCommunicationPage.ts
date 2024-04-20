import { Page } from 'playwright';
import { expect } from '@playwright/test';
import { Post } from 'app/entities/metis/post.model';
import { COURSE_BASE } from '../../constants';

/**
 * A class which encapsulates UI selectors and actions for the course communication page.
 */
export class CourseCommunicationPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Initiates the creation of a new post.
     */
    async newPost() {
        await this.page.locator('#new-post').click();
    }

    /**
     * Gets the context selector within a modal dialog.
     * @returns The locator for the context selector.
     */
    getContextSelectorInModal() {
        return this.page.locator('.modal-content #context');
    }

    /**
     * Sets the title in a modal dialog.
     * @param title - The title to be set.
     */
    async setTitleInModal(title: string) {
        await this.page.locator('.modal-content').locator('#title').fill('');
        await this.page.locator('.modal-content').locator('#title').fill(title);
    }

    /**
     * Sets the content in a modal dialog.
     * @param content - The content to be set.
     */
    async setContentInModal(content: string) {
        const contentField = this.page.locator('.modal-content .markdown-editor .ace_editor');
        await contentField.click();
        await contentField.pressSequentially(content);
    }

    /**
     * Sets the content inline within the editor on the page.
     * @param content - The content to be set.
     */
    async setContentInline(content: string) {
        const contentField = this.page.locator('.markdown-editor-wrapper .markdown-editor .ace_editor');
        await contentField.click();
        await contentField.pressSequentially(content);
    }

    /**
     * Saves the post and waits for the response.
     * @returns A promise that resolves with the response to the save action.
     */
    async save() {
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/posts`);
        await this.page.locator('#save').click();
        return await responsePromise;
    }

    /**
     * Saves a message and waits for the response.
     * @returns A promise that resolves with the response to the save message action.
     */
    async saveMessage() {
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/messages`);
        await this.page.locator('#save').click();
        return await responsePromise;
    }

    /**
     * Verifies that a message post is visible with the specified content.
     * @param postID - The ID of the message post to check.
     * @param content - The content expected to be visible in the message post.
     */
    async checkMessagePost(postID: number, content: string) {
        await expect(this.getSinglePostContent(postID, content)).toBeVisible();
    }

    /**
     * Searches for a message using the provided search term.
     * @param search - The search term to use.
     */
    async searchForMessage(search: string) {
        await this.page.locator('input[name="searchText"]').fill(search);
        await this.page.locator('#search-submit').click();
    }

    /**
     * Applies a filter by context to the messages.
     * @param context - The context to filter by.
     */
    async filterByContext(context: string) {
        await this.page.locator('#filter-context').click();
        await this.page.locator('mat-option', { hasText: context }).locator('.mat-pseudo-checkbox').click();
        await this.page.locator('#filter-context-panel').press('Escape');
    }

    /**
     * Applies a filter to show only the messages created by the current user.
     */
    async filterByOwn() {
        await this.page.locator('#filterToOwn').check();
    }

    /**
     * Applies a filter to show only unresolved messages.
     */
    async filterByUnresolved() {
        await this.page.locator('#filterToUnresolved').check();
    }

    /**
     * Applies a filter to show only messages that have been reacted to.
     */
    async filterByReacted() {
        const filterCheckbox = this.page.locator('#filterToAnsweredOrReacted');
        await filterCheckbox.check();
        await filterCheckbox.click();
    }

    /**
     * Retrieves a single post by its ID.
     * @param postID - The ID of the post to retrieve.
     * @returns The locator for the specified post.
     */
    getSinglePost(postID: number) {
        return this.page.locator(`.items-container #item-${postID}`);
    }

    getSinglePostContent(postID: number, content: string) {
        return this.getSinglePost(postID).locator('.markdown-preview', { hasText: content });
    }

    /**
     * Opens the reply interface for a specified post.
     * @param postID - The ID of the post to reply to.
     */
    async openReply(postID: number) {
        await this.getSinglePost(postID).locator('.reply-btn').click();
    }

    /**
     * Replies to a specified post with the given content.
     * @param postID - The ID of the post to reply to.
     * @param content - The content of the reply.
     */
    async reply(postID: number, content: string) {
        const postElement = this.getSinglePost(postID);
        const postReplyField = postElement.locator('.new-reply-inline-input .markdown-editor .ace_content');
        await postReplyField.click();
        await postReplyField.pressSequentially(content);
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/answer-posts`);
        await postElement.locator('.new-reply-inline-input #save').click();
        await responsePromise;
    }

    /**
     * Replies to a specified post with a message and returns the created Post object.
     * @param postID - The ID of the post to reply to.
     * @param content - The content of the reply.
     * @returns A promise that resolves with the Post object created by the reply.
     */
    async replyWithMessage(postID: number, content: string): Promise<Post> {
        const postElement = this.getSinglePost(postID);
        const postReplyField = postElement.locator('.new-reply-inline-input .markdown-editor .ace_content');
        await postReplyField.click();
        await postReplyField.pressSequentially(content);
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/answer-messages`);
        await this.getSinglePost(postID).locator('.new-reply-inline-input #save').click();
        const response = await responsePromise;
        return response.json();
    }

    /**
     * Reacts to a post with the specified emoji.
     * @param postID - The ID of the post to react to.
     * @param emoji - The emoji to use for the reaction.
     */
    async react(postID: number, emoji: string) {
        await this.getSinglePost(postID).locator('.react').click();
        await this.page.locator('.emoji-mart').locator('.emoji-mart-search input').fill(emoji);
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/postings/reactions`);
        await this.page.locator('.emoji-mart').locator('.emoji-mart-scroll').locator('ngx-emoji').first().click();
        await responsePromise;
    }

    /**
     * Pins the specified post.
     * @param postID - The ID of the post to pin.
     */
    async pinPost(postID: number) {
        await this.getSinglePost(postID).locator('.pin').click();
    }

    /**
     * Deletes the specified post.
     * @param postID - The ID of the post to delete.
     */
    async deletePost(postID: number) {
        const deleteIcon = this.getSinglePost(postID).locator('.deleteIcon');
        await deleteIcon.click();
        await deleteIcon.click();
    }

    /**
     * Edits the content of a message in a specified post.
     * @param postID - The ID of the post containing the message to edit.
     * @param content - The new content for the message.
     */
    async editMessage(postID: number, content: string) {
        const post = this.getSinglePost(postID);
        await post.locator('.editIcon').click();
        await this.setContentInline(content);
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/messages/*`);
        await this.page.locator('#save').click();
        await responsePromise;
    }

    /**
     * Shows the replies for a specified post, expanding them if they are not already visible.
     * @param postID - The ID of the post for which to show replies.
     */
    async showReplies(postID: number) {
        const postElement = this.getSinglePost(postID);
        await postElement.waitFor({ state: 'visible' });
        if (await postElement.locator('.collapse-answers-btn').isHidden()) {
            await this.getSinglePost(postID).locator('.expand-answers-btn').click();
        }
    }

    /**
     * Marks a reply as the answer to a specified post.
     * @param answerID - The ID of the reply to mark as the answer.
     */
    async markAsAnswer(answerID: number) {
        await this.getSinglePost(answerID).locator('.resolve').click();
    }

    /**
     * Verifies that a single post is visible with the specified content.
     * @param postID - The ID of the post to check.
     * @param content - The content expected to be visible in the post.
     */
    async checkSinglePost(postID: number, content: string) {
        await expect(this.getSinglePostContent(postID, content)).toBeVisible();
        await expect(this.getSinglePost(postID).locator('.reference-hash', { hasText: `#${postID}` })).toBeVisible();
    }

    /**
     * Verifies that a single edited post is marked with "edited" notice.
     * @param postID - The ID of the post to check.
     */
    async checkPostEdited(postID: number) {
        await expect(this.getSinglePost(postID).locator('.edited-text', { hasText: '(edited)' })).toBeVisible();
    }

    /**
     * Verifies that a reply is visible with the specified content.
     * @param postID - The ID of the post containing the reply to check.
     * @param content - The content expected to be visible in the reply.
     */
    async checkReply(postID: number, content: string) {
        await expect(this.getSinglePostContent(postID, content)).toBeVisible();
    }

    /**
     * Verifies that a reaction is visible on a post.
     * @param postID - The ID of the post to check for the reaction.
     * @param reaction - The reaction expected to be visible on the post.
     */
    async checkReaction(postID: number, reaction: string) {
        await expect(this.getSinglePost(postID).locator(`.reaction-button.emoji-${reaction}`)).toBeVisible();
    }

    /**
     * Verifies that a post has been marked as resolved.
     * @param postID - The ID of the post to check.
     */
    async checkResolved(postID: number) {
        const pageLocator = this.getSinglePost(postID);
        const messageResolvedIcon = pageLocator.locator(`fa-icon[ng-reflect-ngb-tooltip='Message has been resolved']`);
        await expect(messageResolvedIcon).toBeVisible();
    }

    /**
     * Verifies that a single post is visible at a specific position with the specified title and content.
     * If the title is not provided (undefined), only the content check is performed.
     * @param position - The zero-based index position of the post in the list.
     * @param title - The title of the post. This parameter is optional and may be undefined.
     * @param content - The content expected to be visible in the post.
     */
    async checkSinglePostByPosition(position: number, title: string | undefined, content: string) {
        if (title !== undefined) {
            await expect(this.page.locator('.items-container .item').nth(position).locator('.post-title', { hasText: title })).toBeVisible();
        }
        await expect(this.page.locator('.items-container .item').nth(position).locator('.markdown-preview', { hasText: content })).toBeVisible();
    }

    /**
     * Verifies that a single exercise post is visible with the specified content.
     * @param postID - The ID of the exercise post to check.
     * @param content - The content expected to be visible in the exercise post.
     */
    async checkSingleExercisePost(postID: number, content: string) {
        await expect(this.getSinglePostContent(postID, content)).toBeVisible();
    }
}
