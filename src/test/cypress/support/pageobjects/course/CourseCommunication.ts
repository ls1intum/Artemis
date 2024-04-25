import { COURSE_BASE, POST, PUT } from '../../constants';

/**
 * A class which encapsulates UI selectors and actions for the course communication page.
 */
export class CourseCommunicationPage {
    newPost() {
        cy.get('#new-post').click();
    }

    getContextSelectorInModal() {
        return cy.get('.modal-content #context');
    }

    setTitleInModal(title: string) {
        cy.get('.modal-content').find('#title').clear().type(title);
    }

    setContentInModal(content: string) {
        cy.get('.modal-content').find('.markdown-editor').find('.ace_editor').click().type(content, { delay: 8 });
    }
    setContentInline(content: string) {
        cy.get('.markdown-editor-wrapper').find('.markdown-editor').find('.ace_editor').click().type(content, { delay: 8 });
    }

    save() {
        cy.intercept(POST, `${COURSE_BASE}/*/posts`).as('createPost');
        cy.get('#save').click();
        return cy.wait('@createPost');
    }

    saveMessage() {
        cy.intercept(POST, `${COURSE_BASE}/*/messages`).as('createMessage');
        cy.get('#save').click();
        return cy.wait('@createMessage');
    }

    searchForMessage(search: string) {
        cy.get('input[name="searchText"]').type(search);
        cy.get('#search-submit').click();
    }

    filterByContext(context: string) {
        cy.get('#filter-context').click();
        cy.get('mat-option').filter(`:contains("${context}")`).find('.mat-pseudo-checkbox').click();
        cy.get('#filter-context-panel').type('{esc}');
    }

    filterByOwn() {
        cy.get('#filterToOwn').check();
    }

    filterByUnresolved() {
        cy.get('#filterToUnresolved').check();
    }

    filterByReacted() {
        cy.get('#filterToAnsweredOrReacted').check().click();
    }

    getSinglePost(postID: number) {
        return cy.get(`.items-container #item-${postID}`);
    }

    openReply(postID: number) {
        this.getSinglePost(postID).find('.reply-btn').click();
    }

    reply(postID: number, content: string) {
        this.getSinglePost(postID).find('.new-reply-inline-input').find('.markdown-editor').find('.ace_content').click().type(content, { delay: 8 });
        cy.intercept(POST, `${COURSE_BASE}/*/answer-posts`).as('createReply');
        this.getSinglePost(postID).find('.new-reply-inline-input').find('#save').click();
        return cy.wait('@createReply');
    }

    replyWithMessage(postID: number, content: string) {
        this.getSinglePost(postID).find('.new-reply-inline-input').find('.markdown-editor').find('.ace_content').click().type(content, { delay: 8 });
        cy.intercept(POST, `${COURSE_BASE}/*/answer-messages`).as('createReply');
        this.getSinglePost(postID).find('.new-reply-inline-input').find('#save').click();
        return cy.wait('@createReply');
    }

    react(postID: number, emoji: string) {
        this.getSinglePost(postID).find('.react').click();
        cy.get('.emoji-mart').find('.emoji-mart-search input').type(emoji);
        cy.intercept(POST, `${COURSE_BASE}/*/postings/reactions`).as('createReaction');
        cy.get('.emoji-mart').find('.emoji-mart-scroll').find('ngx-emoji:first()').click();
        return cy.wait('@createReaction');
    }

    pinPost(postID: number) {
        this.getSinglePost(postID).find('.pin').click();
    }

    deletePost(postID: number) {
        this.getSinglePost(postID).find('.deleteIcon').click().click();
    }

    editMessage(postID: number, content: string) {
        const post = this.getSinglePost(postID);
        post.find('.editIcon').click();
        this.setContentInline(content);
        cy.intercept(PUT, `${COURSE_BASE}/*/messages/*`).as('updatePost');
        cy.get('#save').click();
        cy.wait('@updatePost');
    }

    showReplies(postID: number) {
        this.getSinglePost(postID).find('.expand-answers-btn').click();
    }

    markAsAnswer(answerID: number) {
        this.getSinglePost(answerID).find('.resolve').click();
    }

    checkSinglePost(postID: number, content: string) {
        this.getSinglePost(postID).find('.markdown-preview').contains(content);
        this.getSinglePost(postID).find('.reference-hash').contains(`#${postID}`);
    }

    checkReply(postID: number, content: string) {
        this.getSinglePost(postID).find('.markdown-preview').contains(content);
    }

    checkReaction(postID: number, reaction: string) {
        this.getSinglePost(postID).find(`.reaction-button.emoji-${reaction}`).should('exist');
    }

    checkResolved(postID: number) {
        this.getSinglePost(postID).find('fa-icon.resolved').should('exist');
    }

    checkSinglePostByPosition(position: number, title: string | undefined, content: string) {
        if (title !== undefined) {
            cy.get('.items-container .item').eq(position).find('.post-title').contains(title);
        }
        cy.get('.items-container .item').eq(position).find('.markdown-preview').contains(content);
    }

    checkSingleExercisePost(postID: number, content: string) {
        this.getSinglePost(postID).find('.markdown-preview').contains(content);
    }
}
