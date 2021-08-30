import { COURSE_MANAGEMENT_BASE } from '../../../requests/CourseManagementRequests';

/**
 * A class which encapsulates UI selectors and actions for the Online Editor Page.
 */
export class CodeAnalysisGradingPage {
    visit(courseId: number, exerciseId: number) {
        cy.visit(`${COURSE_MANAGEMENT_BASE}${courseId}/programming-exercises/${exerciseId}/grading/code-analysis`);
    }

    makeEveryScaCategoryInfluenceGrading() {
        cy.get('select').each((category) => {
            cy.wrap(category).select('GRADING');
        });
    }
}
