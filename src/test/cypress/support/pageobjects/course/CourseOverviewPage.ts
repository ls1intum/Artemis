import { BASE_API, GET, POST } from '../../constants';
import { COURSE_BASE, CypressExerciseType } from '../../requests/CourseManagementRequests';

/**
 * A class which encapsulates UI selectors and actions for the course overview page (/courses/*).
 */
export class CourseOverviewPage {
    readonly participationRequestId = 'participateInExerciseQuery';

    private getExerciseCardRootElement(exerciseName: string) {
        return cy.get('.course-body-container').contains(exerciseName).parents('.course-exercise-row');
    }

    startExercise(exerciseName: string, exerciseType: CypressExerciseType) {
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
        this.getExerciseCardRootElement(exerciseName).find('button').click();
        cy.wait('@participateInExerciseQuery');
    }

    openRunningExercise(exerciseTitle: string) {
        this.getExerciseCardRootElement(exerciseTitle).find('[buttonicon="folder-open"]').click();
    }

    openRunningProgrammingExercise(exerciseTitle: string) {
        cy.intercept(GET, BASE_API + 'programming-exercise-participations/*/student-participation-with-latest-result-and-feedbacks').as('initialQuery');
        this.openRunningExercise(exerciseTitle);
        cy.wait('@initialQuery');
    }

    openExamsTab() {
        this.getTabBar().find('[jhitranslate="artemisApp.courseOverview.menu.exams"]').click();
    }

    openExam(examTitle: string) {
        this.getExamsRootElement().contains(examTitle).click();
    }

    private getTabBar() {
        return cy.get('.tab-bar-course-overview');
    }

    private getExamsRootElement() {
        return cy.get('jhi-course-exams');
    }
}
