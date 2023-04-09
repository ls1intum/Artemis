import { BASE_API, POST } from '../../constants';

/**
 * A class which encapsulates UI selectors and actions for the course creation page.
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

    joinChannel(channelID: number) {
        cy.intercept(POST, BASE_API + 'courses/*/channels/*/register').as('joinChannel');
        cy.get(`#channel-${channelID}`).find(`#register${channelID}`).click({ force: true });
        cy.wait('@joinChannel');
    }

    leaveChannel(channelID: number) {
        cy.intercept(POST, BASE_API + 'courses/*/channels/*/deregister').as('leaveChannel');
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
        cy.intercept(POST, BASE_API + 'courses/*/channels').as('createChannel');
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
        cy.get('.channels-overview').find('#description').clear().type(newDescription);
        cy.get('#submitButton').click();
    }

    closeEditPanel() {
        cy.get('.conversation-detail-dialog').find('.btn-close').click();
    }
}
