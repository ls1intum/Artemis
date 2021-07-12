describe('footer test', () => {
    before('visit home', () => {
        cy.visit('/');
    });
    it('.fixed footer', () => {
        cy.get('.fixed').should('exist');
    });
    it('About us', () => {
        cy.contains('About us').should('have.attr', 'href', '/about');
    });
    it('Release Notes', () => {
        cy.contains('Release Notes')
            .should('have.attr', 'href')
            .and('match', /releases/);
    });
    it('Privacy Statement', () => {
        cy.contains('Privacy Statement').should('have.attr', 'href', '/privacy');
    });
    it('Imprint', () => {
        cy.contains('Imprint').should('have.attr', 'href', '/imprint');
    });
});
