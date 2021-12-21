import { BASE_API, GET, POST } from '../../constants';
import { COURSE_BASE, CypressExerciseType } from '../../requests/CourseManagementRequests';

/**
 * A class which encapsulates UI selectors and actions for the course overview page (/courses/*).
 */
export class CourseOverviewPage {
    readonly participationRequestId = 'participateInExerciseQuery';

    startExercise(exerciseId: string, exerciseType: CypressExerciseType) {
        switch (exerciseType) {
            case CypressExerciseType.MODELING:
            case CypressExerciseType.TEXT:
            case CypressExerciseType.PROGRAMMING:
                cy.intercept(POST, COURSE_BASE + '*/exercises/*/participations').as(this.participationRequestId);
                break;
            case CypressExerciseType.QUIZ:
                cy.intercept(GET, BASE_API + 'exercises/*/participation').as(this.participationRequestId);
                break;
            default:
                throw new Error(`Exercise type '${exerciseType}' is not supported yet!`);
        }
        cy.get('#start-exercise-' + exerciseId).click();
        cy.wait('@participateInExerciseQuery');
    }

    openRunningExercise(exerciseId: string) {
        cy.get('#open-exercise-' + exerciseId).click();
    }

    openRunningProgrammingExercise(exerciseId: string) {
        cy.intercept(GET, BASE_API + 'programming-exercise-participations/*/student-participation-with-latest-result-and-feedbacks').as('initialQuery');
        this.openRunningExercise(exerciseId);
        cy.wait('@initialQuery');
    }

    openExamsTab() {
        this.getTabBar().find('#exams-tab').click();
    }

    openExam(examId: string) {
        cy.get('#exam-' + examId).click();
    }

    private getTabBar() {
        return cy.get('#tab-bar');
    }
}
