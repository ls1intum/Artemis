import { MODELING_EXERCISE_BASE } from '../requests/CourseManagementRequests';

/**
 * A class which encapsulates UI selectors and actions for the Modeling Exercise Creation Page.
 * Path: /course-management/{courseID}/modeling-exercises/{exerciseID}
 */
export class CreateModelingExercisePage {
    DIFFICULTY_BAR = 'jhi-difficulty-picker > :nth-child(1) >';
    setTitle(title: string) {
        cy.get('#field_title').clear().type(title);
    }

    addCategories(categories: string[]) {
        categories.forEach((category) => {
            cy.get('#field_categories').type(category);
            // this line is a hack so the category ends
            cy.get('#id').click({ force: true });
        });
    }

    setPoints(points: number) {
        cy.get('#field_points').clear().type(points.toString());
    }

    save() {
        cy.intercept(MODELING_EXERCISE_BASE).as('createModelingExercise');
        cy.get('[jhitranslate="entity.action.save"]').click();
        return cy.wait('@createModelingExercise');
    }

    /**
     * Sets the release Date field
     * @param date should be in Format: YYYY-MM-DDTHH:mm:ss.SSS
     * */
    setReleaseDate(date: string) {
        cy.get(':nth-child(1) > jhi-date-time-picker.ng-untouched > .d-flex > .form-control').clear().type(date, { force: true });
    }

    /**
     * Sets the Due Date field
     * @param date should be in Format: YYYY-MM-DDTHH:mm:ss.SSS
     * */
    setDueDate(date: string) {
        cy.get('.ms-3 > jhi-date-time-picker.ng-untouched > .d-flex > .form-control').clear().type(date, { force: true });
    }

    /**
     * Sets the Assessment Due Date field
     * @param date should be in Format: YYYY-MM-DDTHH:mm:ss.SSS
     * */
    setAssessmentDueDate(date: string) {
        cy.get(':nth-child(9) > jhi-date-time-picker.ng-untouched > .d-flex > .form-control').clear().type(date, { force: true });
    }

    includeInOverallScore() {
        cy.get('jhi-included-in-overall-score-picker > .btn-group > :nth-child(3)').click({ force: true });
    }

    pickDifficulty(options: { hard?: boolean; medium?: boolean; easy?: boolean }) {
        if (options.hard) {
            cy.get(this.DIFFICULTY_BAR).eq(3).click();
        } else if (options.medium) {
            cy.get(this.DIFFICULTY_BAR).eq(2).click();
        } else if (options.easy) {
            cy.get(this.DIFFICULTY_BAR).eq(1).click();
        } else {
            cy.get(this.DIFFICULTY_BAR).eq(0).click();
        }
    }
}
