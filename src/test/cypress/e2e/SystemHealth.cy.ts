import { admin } from '../support/users';

describe('Check artemis system health', () => {
    beforeEach('Login as admin and visit system health page', () => {
        cy.login(admin, '/admin/health');
    });

    it('Checks continuous integration health', () => {
        cy.get('#healthCheck #continuousIntegrationServer .status').contains('UP');
    });

    it('Checks version control health', () => {
        cy.get('#healthCheck #versionControlServer .status').contains('UP');
    });

    it('Checks user management health', () => {
        cy.get('#healthCheck #userManagement .status').contains('UP');
    });

    it('Checks database health', () => {
        cy.get('#healthCheck #db .status').contains('UP');
    });

    it('Checks hazelcast health', () => {
        cy.get('#healthCheck #hazelcast .status').contains('UP');
    });

    it('Checks ping health', () => {
        cy.get('#healthCheck #ping .status').contains('UP');
    });

    it('Checks readiness health', () => {
        cy.get('#healthCheck #readinessState .status').contains('UP');
    });

    it('Checks websocket broker health', () => {
        cy.get('#healthCheck #websocketBroker .status').contains('UP');
    });

    it('Checks websocket connection health', () => {
        cy.get('#healthCheck #websocketConnection .status').contains('Connected');
    });
});
