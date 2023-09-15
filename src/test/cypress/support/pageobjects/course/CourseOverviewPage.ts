import { BASE_API, GET } from '../../constants';

/**
 * A class which encapsulates UI selectors and actions for the course overview page (/courses/*).
 */
export class CourseOverviewPage {
    readonly participationRequestId = 'participateInExerciseQuery';

    search(term: string): void {
        cy.get('#exercise-search-input').type(term);
        cy.get('#exercise-search-button').click();
    }

    startExercise(exerciseId: number) {
        cy.reloadUntilFound('#start-exercise-' + exerciseId);
        cy.get('#start-exercise-' + exerciseId).click();
    }

    openRunningExercise(exerciseId: number) {
        cy.reloadUntilFound('#open-exercise-' + exerciseId);
        cy.get('#open-exercise-' + exerciseId).click();
    }

    getExercise(exerciseID: number) {
        return cy.get(`#exercise-card-${exerciseID}`);
    }

    openRunningProgrammingExercise(exerciseID: number) {
        cy.intercept(GET, BASE_API + 'programming-exercise-participations/*/student-participation-with-latest-result-and-feedbacks').as('initialQuery');
        this.openRunningExercise(exerciseID);
        cy.wait('@initialQuery');
    }

    openExamsTab() {
        cy.get('#exam-tab').click();
    }

    openExam(examId: number) {
        cy.get('#exam-' + examId).click();
    }
}
