import { CourseWideContext } from '../../constants';
import { titleCaseWord } from '../../utils';

/**
 * A class which encapsulates UI selectors and actions for the course creation page.
 */
export class CourseCommunicationPage {
    newPost() {
        cy.get('#new-post').click();
    }

    selectContext(context: CourseWideContext) {
        cy.get('.modal-content #context').select(titleCaseWord(context));
    }

    getContextSelector() {
        return cy.get('.modal-content #context');
    }

    setTitle(title: string) {
        cy.get('.modal-content').find('#title').clear().type(title);
    }

    setContent(content: string) {
        cy.get('.modal-content').find('.markdown-editor').find('.ace_editor').click().type(content, { delay: 8 });
    }

    save() {
        cy.get('#save').click();
    }

    searchForPost(search: string) {
        cy.get('#search').type(search);
        cy.get('#searchSubmit').click();
    }

    filterByContext(context: CourseWideContext) {
        cy.get('#filter-context').select(titleCaseWord(context));
    }

    getSinglePost(postID: number) {
        return cy.get(`.items-container #item-${postID}`);
    }

    reply(postID: number, content: string) {
        this.getSinglePost(postID).find('.reply-btn').click();
        this.getSinglePost(postID).find('.new-reply-inline-input').find('.markdown-editor').find('.ace_content').click().type(content, { delay: 8 });
        this.getSinglePost(postID).find('.new-reply-inline-input').find('#save').click();
    }

    deletePost(postID: number) {
        const post = this.getSinglePost(postID);
        post.find('.deleteIcon').click().click();
    }

    editPost(postID: number, title: string, content: string) {
        const post = this.getSinglePost(postID);
        post.find('.editIcon').click();
        this.setTitle(title);
        this.setContent(content);
        this.save();
    }

    checkSinglePost(postID: number, title: string, content: string, context: CourseWideContext) {
        this.getSinglePost(postID).find('.context-information').contains(titleCaseWord(context));
        this.getSinglePost(postID).find('.post-title').contains(title);
        this.getSinglePost(postID).find('.markdown-preview').contains(content);
        this.getSinglePost(postID).find('.reference-hash').contains(`#${postID}`);
    }
}
