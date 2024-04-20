import { Dayjs } from 'dayjs/esm';

import { MODELING_EXERCISE_BASE, POST } from '../../../constants';
import { enterDate } from '../../../utils';

/**
 * A class which encapsulates UI selectors and actions for the Modeling Exercise Creation Page.
 * Path: /course-management/{courseID}/modeling-exercises/{exerciseID}
 */
export class CreateModelingExercisePage {
    setTitle(title: string) {
        cy.get('#field_title').clear().type(title);
    }

    addCategories(categories: string[]) {
        categories.forEach((category) => {
            cy.get('#field_categories').type(category).type('{enter}');
        });
    }

    setPoints(points: number) {
        cy.get('#field_points').clear().type(points.toString());
    }

    save() {
        cy.intercept(MODELING_EXERCISE_BASE).as('createModelingExercise');
        cy.get('#save-entity').click();
        return cy.wait('@createModelingExercise');
    }

    import() {
        cy.intercept(POST, `${MODELING_EXERCISE_BASE}/import/*`).as('modelingExerciseImport');
        cy.get('#save-entity').click();
        return cy.wait('@modelingExerciseImport');
    }

    /**
     * Sets the release Date field
     * @param date should be in Format: YYYY-MM-DDTHH:mm:ss.SSS
     * */
    setReleaseDate(date: Dayjs) {
        enterDate('#pick-releaseDate', date);
    }

    /**
     * Sets the Due Date field
     * @param date should be in Format: YYYY-MM-DDTHH:mm:ss.SSS
     * */
    setDueDate(date: Dayjs) {
        enterDate('#pick-dueDate', date);
    }

    /**
     * Sets the Assessment Due Date field
     * @param date should be in Format: YYYY-MM-DDTHH:mm:ss.SSS
     * */
    setAssessmentDueDate(date: Dayjs) {
        enterDate('#pick-assessmentDueDate', date);
    }

    includeInOverallScore() {
        cy.get('#modeling-includeInScore-picker').children().eq(0).children().eq(2).click({ force: true });
    }

    pickDifficulty(options: { hard?: boolean; medium?: boolean; easy?: boolean }) {
        if (options.hard) {
            this.getDiffifultyBar().children().eq(3).click();
        } else if (options.medium) {
            this.getDiffifultyBar().children().eq(2).click();
        } else if (options.easy) {
            this.getDiffifultyBar().children().eq(1).click();
        } else {
            this.getDiffifultyBar().children().eq(0).click();
        }
    }

    private getDiffifultyBar() {
        return cy.get('#modeling-difficulty-picker').children().eq(0);
    }
}
