import { BASE_API, GET } from '../../constants';

/**
 * A class which encapsulates UI selectors and actions for the course overview page (/courses/*).
 */
export class CourseOverviewPage {
    readonly participationRequestId = 'participateInExerciseQuery';

    search(term: string): void {
        cy.get('input[formcontrolname="searchFilter"]').type(term);
    }

    startExercise(exerciseId: number, refreshInterval?: number) {
        cy.reloadUntilFound('#start-exercise-' + exerciseId, refreshInterval);
        cy.get('#start-exercise-' + exerciseId).click();
    }

    openRunningExercise(exerciseId: number) {
        cy.reloadUntilFound('#open-exercise-' + exerciseId);
        cy.get('#open-exercise-' + exerciseId).click();
    }

    getExercise(exerciseTitle: string) {
        return cy.contains('#test-sidebar-card', exerciseTitle);
    }

    openExerciseOverview(exerciseTitle: string) {
        this.getExercise(exerciseTitle).click();
    }

    getExercises() {
        return cy.get('#test-sidebar-card');
    }

    openRunningProgrammingExercise(exerciseID: number) {
        cy.intercept(GET, `${BASE_API}/programming-exercise-participations/*/student-participation-with-latest-result-and-feedbacks`).as('initialQuery');
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
