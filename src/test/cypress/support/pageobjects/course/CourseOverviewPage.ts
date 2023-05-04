import { BASE_API, GET } from '../../constants';

/**
 * A class which encapsulates UI selectors and actions for the course overview page (/courses/*).
 */
export class CourseOverviewPage {
    readonly participationRequestId = 'participateInExerciseQuery';

    startExercise(exerciseId: number) {
        cy.reloadUntilFound('#start-exercise-' + exerciseId);
        cy.get('#start-exercise-' + exerciseId).click();
    }

    openRunningExercise(exerciseId: number) {
        cy.reloadUntilFound('#open-exercise-' + exerciseId);
        cy.get('#open-exercise-' + exerciseId).click();
    }

    openRunningProgrammingExercise(exerciseId: number) {
        cy.intercept(GET, BASE_API + 'programming-exercise-participations/*/student-participation-with-latest-result-and-feedbacks').as('initialQuery');
        this.openRunningExercise(exerciseId);
        cy.wait('@initialQuery');
    }

    openExamsTab() {
        cy.get('#exam-tab').click();
    }

    openExam(examId: number) {
        cy.get('#exam-' + examId).click();
    }
}
