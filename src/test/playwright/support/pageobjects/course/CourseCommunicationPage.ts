import { Page } from 'playwright';
import { BASE_API } from '../../constants';
import { expect } from '@playwright/test';
import { Post } from 'app/entities/metis/post.model';

/**
 * A class which encapsulates UI selectors and actions for the course communication page.
 */
export class CourseCommunicationPage {
    private page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async newPost() {
        await this.page.locator('#new-post').click();
    }

    getContextSelectorInModal() {
        return this.page.locator('.modal-content #context');
    }

    async setTitleInModal(title: string) {
        await this.page.locator('.modal-content').locator('#title').fill('');
        await this.page.locator('.modal-content').locator('#title').fill(title);
    }

    async setContentInModal(content: string) {
        const contentField = this.page.locator('.modal-content .markdown-editor .ace_editor');
        await contentField.click();
        await contentField.pressSequentially(content);
    }

    async setContentInline(content: string) {
        const contentField = this.page.locator('.markdown-editor-wrapper .markdown-editor .ace_editor');
        await contentField.click();
        await contentField.pressSequentially(content);
    }

    async save() {
        const responsePromise = this.page.waitForResponse(`${BASE_API}courses/*/posts`);
        await this.page.locator('#save').click();
        await responsePromise;
    }

    async saveMessage() {
        const responsePromise = this.page.waitForResponse(`${BASE_API}courses/*/messages`);
        await this.page.locator('#save').click();
        await responsePromise;
    }

    async searchForMessage(search: string) {
        await this.page.locator('#search').fill(search);
        await this.page.locator('#search-submit').click();
    }

    async filterByContext(context: string) {
        await this.page.locator('#filter-context').click();
        await this.page.locator('mat-option', { hasText: context }).locator('.mat-pseudo-checkbox').click();
        await this.page.locator('#filter-context-panel').press('Escape');
    }

    async filterByOwn() {
        await this.page.locator('#filterToOwn').check();
    }

    async filterByUnresolved() {
        await this.page.locator('#filterToUnresolved').check();
    }

    async filterByReacted() {
        const filterCheckbox = this.page.locator('#filterToAnsweredOrReacted');
        await filterCheckbox.check();
        await filterCheckbox.click();
    }

    getSinglePost(postID: number) {
        return this.page.locator(`.items-container #item-${postID}`);
    }

    async openReply(postID: number) {
        await this.getSinglePost(postID).locator('.reply-btn').click();
    }

    async reply(postID: number, content: string) {
        const postElement = this.getSinglePost(postID);
        const postReplyField = postElement.locator('.new-reply-inline-input .markdown-editor .ace_content');
        await postReplyField.click();
        await postReplyField.pressSequentially(content);
        const responsePromise = this.page.waitForResponse(`${BASE_API}courses/*/answer-posts`);
        await postElement.locator('.new-reply-inline-input #save').click();
        await responsePromise;
    }

    async replyWithMessage(postID: number, content: string): Promise<Post> {
        const postElement = this.getSinglePost(postID);
        const postReplyField = postElement.locator('.new-reply-inline-input .markdown-editor .ace_content');
        await postReplyField.click();
        await postReplyField.pressSequentially(content);
        const responsePromise = this.page.waitForResponse(`${BASE_API}courses/*/answer-messages`);
        await this.getSinglePost(postID).locator('.new-reply-inline-input #save').click();
        const response = await responsePromise;
        return response.json();
    }

    async react(postID: number, emoji: string) {
        await this.getSinglePost(postID).locator('.react').click();
        await this.page.locator('.emoji-mart').locator('.emoji-mart-search input').fill(emoji);
        const responsePromise = this.page.waitForResponse(`${BASE_API}courses/*/postings/reactions`);
        await this.page.locator('.emoji-mart').locator('.emoji-mart-scroll').locator('ngx-emoji').first().click();
        await responsePromise;
    }

    async pinPost(postID: number) {
        await this.getSinglePost(postID).locator('.pin').click();
    }

    async deletePost(postID: number) {
        const deleteIcon = this.getSinglePost(postID).locator('.deleteIcon');
        await deleteIcon.click();
        await deleteIcon.click();
    }

    async editMessage(postID: number, content: string) {
        const post = this.getSinglePost(postID);
        await post.locator('.editIcon').click();
        await this.setContentInline(content);
        const responsePromise = this.page.waitForResponse(`${BASE_API}courses/*/messages/*`);
        await this.page.locator('#save').click();
        await responsePromise;
    }

    async showReplies(postID: number) {
        const postElement = this.getSinglePost(postID);
        await postElement.waitFor({ state: 'visible' });
        if (await postElement.locator('.collapse-answers-btn').isHidden()) {
            await this.getSinglePost(postID).locator('.expand-answers-btn').click();
        }
    }

    async markAsAnswer(answerID: number) {
        await this.getSinglePost(answerID).locator('.resolve').click();
    }

    async checkSinglePost(postID: number, content: string) {
        await expect(this.getSinglePost(postID).locator('.markdown-preview', { hasText: content })).toBeVisible();
        await expect(this.getSinglePost(postID).locator('.reference-hash', { hasText: `#${postID}` })).toBeVisible();
    }

    async checkReply(postID: number, content: string) {
        await expect(this.getSinglePost(postID).locator('.markdown-preview', { hasText: content })).toBeVisible();
    }

    async checkReaction(postID: number, reaction: string) {
        await expect(this.getSinglePost(postID).locator(`.reaction-button.emoji-${reaction}`)).toBeVisible();
    }

    async checkResolved(postID: number) {
        const pageLocator = this.getSinglePost(postID);
        const messageResolvedIcon = pageLocator.locator(`fa-icon[ng-reflect-ngb-tooltip='Message has been resolved']`);
        await expect(messageResolvedIcon).toBeVisible();
    }

    async checkSinglePostByPosition(position: number, title: string | undefined, content: string) {
        if (title !== undefined) {
            await expect(this.page.locator('.items-container .item').nth(position).locator('.post-title', { hasText: title })).toBeVisible();
        }
        await expect(this.page.locator('.items-container .item').nth(position).locator('.markdown-preview', { hasText: content })).toBeVisible();
    }

    async checkSingleExercisePost(postID: number, content: string) {
        await expect(this.getSinglePost(postID).locator('.markdown-preview', { hasText: content })).toBeVisible();
    }
}
