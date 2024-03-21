import { COURSE_BASE, DELETE, POST, PUT } from '../../constants';

/**
 * A class which encapsulates UI selectors and actions for the course messages page.
 */
export class CourseMessagesPage {
    createChannelButton() {
        cy.get('#channelButton').click();
        cy.get('#createChannel').click();
    }

    browseChannelsButton() {
        cy.get('#channelButton').click();
        cy.get('#channelOverview').click();
    }

    browseExerciseChannelsButton() {
        cy.get('#exerciseChannelButton').click();
        cy.get('#exerciseChannelOverview').click();
    }

    browseLectureChannelsButton() {
        cy.get('#lectureChannelButton').click();
        cy.get('#lectureChannelOverview').click();
    }

    browseExamChannelsButton() {
        cy.get('#examChannelButton').click();
        cy.get('#examChannelOverview').click();
    }

    checkChannelsExists(name: string) {
        cy.get('.channels-overview').find('.list-group-item').contains(name);
    }

    getChannelIdByName(name: string) {
        return cy
            .get('.channels-overview')
            .find('.list-group-item')
            .filter(`:contains("${name}")`)
            .invoke('attr', 'id')
            .then((id) => {
                return id?.replace('channel-', '');
            });
    }

    joinChannel(channelID: number) {
        cy.intercept(POST, `${COURSE_BASE}/*/channels/*/register`).as('joinChannel');
        cy.get(`#channel-${channelID}`).find(`#register${channelID}`).click({ force: true });
        cy.wait('@joinChannel');
    }

    leaveChannel(channelID: number) {
        cy.intercept(POST, `${COURSE_BASE}/*/channels/*/deregister`).as('leaveChannel');
        cy.get(`#channel-${channelID}`).find(`#deregister${channelID}`).click({ force: true });
        cy.wait('@leaveChannel');
    }

    checkBadgeJoined(channelID: number) {
        return cy.get(`#channel-${channelID}`).find('.badge');
    }

    setName(name: string) {
        cy.get('.modal-content').find('#name').clear().type(name);
    }

    setDescription(description: string) {
        cy.get('.modal-content').find('#description').clear().type(description);
    }

    setPrivate() {
        cy.get('.modal-content').find('label[for=private]').click();
    }

    setPublic() {
        cy.get('.modal-content').find('label[for=public]').click();
    }

    setAnnouncementChannel() {
        cy.get('.modal-content').find('label[for=isAnnouncementChannel]').click();
    }

    setUnrestrictedChannel() {
        cy.get('.modal-content').find('label[for=isNotAnnouncementChannel]').click();
    }

    createChannel(isAnnouncementChannel: boolean, isPublic: boolean) {
        cy.intercept(POST, `${COURSE_BASE}/*/channels`).as('createChannel');
        cy.get('.modal-content').find('#submitButton').click();
        cy.wait('@createChannel').then((interception) => {
            const response = interception.response!.body;
            cy.url().should('contain', `messages?conversationId=${response.id}`);
            expect(response.isAnnouncementChannel).to.eq(isAnnouncementChannel);
            expect(response.isPublic).to.eq(isPublic);
        });
    }

    getError() {
        return cy.get('.modal-body').find('.alert');
    }

    getName() {
        return cy.get('h3.conversation-name');
    }

    getTopic() {
        return cy.get('.conversation-topic');
    }

    editName(newName: string) {
        cy.get('#name-section').find('.action-button').click();
        cy.get('.channels-overview').find('#name').clear().type(newName);
        cy.get('#submitButton').click();
    }

    editTopic(newTopic: string) {
        cy.get('#topic-section').find('.action-button').click();
        cy.get('.channels-overview').find('#topic').clear().type(newTopic);
        cy.get('#submitButton').click();
    }

    editDescription(newDescription: string) {
        cy.get('#description-section').find('.action-button').click();
        cy.get('.channels-overview').find('#description').type(newDescription);
        cy.get('#submitButton').click();
    }

    closeEditPanel() {
        cy.get('.conversation-detail-dialog').find('.btn-close').click();
    }

    writeMessage(message: string) {
        cy.get('.markdown-editor').find('.ace_editor').click().type(message, { delay: 8 });
    }

    checkMessage(messageId: number, message: string) {
        this.getSinglePost(messageId).find('.markdown-preview').contains(message);
    }

    editMessage(messageId: number, message: string) {
        this.getSinglePost(messageId).find('.editIcon').click();
        this.getSinglePost(messageId).find('.markdown-editor').find('.ace_editor').click().type(message, { delay: 8 });
        cy.intercept(PUT, `${COURSE_BASE}/*/messages/*`).as('updateMessage');
        this.getSinglePost(messageId).find('#save').click();
        cy.wait('@updateMessage');
    }

    deleteMessage(messageId: number) {
        cy.intercept(DELETE, `${COURSE_BASE}/*/messages/*`).as('deleteMessage');
        this.getSinglePost(messageId).find('.deleteIcon').click();
        this.getSinglePost(messageId).find('.deleteIcon').click();
        cy.wait('@deleteMessage');
    }

    getSinglePost(postID: number) {
        return cy.get(`#item-${postID}`);
    }

    save(force = false) {
        cy.intercept(POST, `${COURSE_BASE}/*/messages`).as('createMessage');
        cy.get('#save').click({ force });
        return cy.wait('@createMessage');
    }

    createGroupChatButton() {
        cy.get('#createGroupChat').click();
    }

    createGroupChat() {
        cy.intercept(POST, `${COURSE_BASE}/*/group-chats`).as('createGroupChat');
        cy.get('#submitButton').click();
        return cy.wait('@createGroupChat');
    }

    updateGroupChat() {
        cy.intercept(POST, `${COURSE_BASE}/*/group-chats/*/register`).as('updateGroupChat');
        cy.get('#submitButton').click();
        cy.wait('@updateGroupChat');
    }

    addUserToGroupChat(user: string) {
        cy.get('#users-selector0-user-input').type(user);
        cy.get('#ngb-typeahead-0')
            .contains(new RegExp('\\(' + user + '\\)'))
            .click();
    }

    addUserToGroupChatButton() {
        cy.get('.addUsers').click();
    }

    listMembersButton(courseID: number, conversationID: number) {
        cy.visit(`/courses/${courseID}/messages?conversationId=${conversationID}`);
        cy.get('.members').click();
    }

    checkMemberList(name: string) {
        cy.get('jhi-conversation-members').contains(name);
    }

    openSettingsTab() {
        cy.get('.settings-tab').click();
    }

    leaveGroupChat() {
        cy.get('.leave-conversation').click();
    }

    checkGroupChatExists(name: string, exist: boolean) {
        if (exist) {
            cy.get('.conversation-list').should('contain.text', name);
        } else {
            cy.get('.conversation-list').should('not.contain.text', name);
        }
    }

    acceptCodeOfConductButton() {
        cy.get('#acceptCodeOfConductButton').click();
    }
}
