import { BASE_API, GET, POST } from '../../constants';
import { CypressExerciseType } from '../../requests/CourseManagementRequests';

/**
 * A class which encapsulates UI selectors and actions for the course overview page (/courses/*).
 */
export class CourseOverviewPage {
    private getExerciseCardRootElement(exerciseName: string) {
        return cy.get('.course-body-container').contains(exerciseName).parents('.course-exercise-row');
    }

    startExercise(exerciseName: string, exerciseType: CypressExerciseType) {
        cy.intercept(POST, BASE_API + 'courses/*/exercises/*/participations').as('participateInExerciseQuery');
        switch (exerciseType) {
            case CypressExerciseType.MODELING:
                this.getExerciseCardRootElement(exerciseName).find('.btn-sm').should('contain.text', 'Start exercise').click();
                break;
            case CypressExerciseType.TEXT:
                this.getExerciseCardRootElement(exerciseName).find('.start-exercise').click();
                break;
            default:
                throw new Error(`Exercise type '${exerciseType}' is not supported yet!`);
        }
        cy.wait('@participateInExerciseQuery');
    }

    openRunningExercise(exerciseTitle: string) {
        this.getExerciseCardRootElement(exerciseTitle).find('[buttonicon="folder-open"]').click();
    }

    openRunningProgrammingExercise(exerciseTitle: string) {
        cy.intercept(GET, BASE_API + 'programming-exercise-participations/*/student-participation-with-latest-result-and-feedbacks').as('initialQuery');
        this.openRunningExercise(exerciseTitle);
        cy.wait('@initialQuery').wait(2000);
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
