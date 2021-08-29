/**
 * Parent class for all exercise feedback pages (/course/./exercise/./participate/.)
 */
export abstract class AbstractExerciseFeedback {
    shouldShowFeedback(points: number, feedbackText: string) {
        cy.get('.unreferencedFeedback').contains(`${points} Points: ${feedbackText}`).should('be.visible');
    }

    shouldShowScore(achievedPoints: number, maxPoints: number, percentage: number) {
        cy.get('jhi-result').contains(`Score ${percentage}%, ${achievedPoints} of ${maxPoints} points`);
    }

    clickComplain() {
        cy.get('.btn-primary').contains('Complain').click();
        cy.wait(1000);
    }
}
