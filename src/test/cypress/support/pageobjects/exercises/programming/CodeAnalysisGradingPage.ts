import { PATCH, PROGRAMMING_EXERCISE_BASE } from '../../../constants';

/**
 * A class which encapsulates UI selectors and actions for static code analysis grading configuration page.
 */
export class CodeAnalysisGradingPage {
    visit(courseId: number, exerciseId: number) {
        cy.visit(`course-management/${courseId}/programming-exercises/${exerciseId}/grading/code-analysis`);
    }

    makeEveryScaCategoryInfluenceGrading() {
        // Using ids here would make the test more instable. Its unlikely that this selector will break in the future.
        cy.get('select').each((category) => {
            cy.wrap(category).select('GRADED');
        });
    }

    saveChanges() {
        cy.intercept(PATCH, `${PROGRAMMING_EXERCISE_BASE}/*/static-code-analysis-categories`).as('scaConfigurationRequest');
        cy.get('#save-table-button').click();
        return cy.wait('@scaConfigurationRequest');
    }
}
