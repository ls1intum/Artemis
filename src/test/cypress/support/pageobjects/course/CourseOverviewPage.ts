import { POST, BASE_API, GET } from '../../constants';

/**
 * A class which encapsulates UI selectors and actions for the course overview page (/courses/*).
 */
export class CourseOverviewPage {
    private getExerciseCardRootElement(exerciseName: string) {
        return cy.get('.course-body-container').contains(exerciseName).parents('.course-exercise-row');
    }

    startExercise(exerciseName: string) {
        cy.intercept(POST, BASE_API + 'courses/*/exercises/*/participations').as('participateInExerciseQuery');
        this.getExerciseCardRootElement(exerciseName).find('.start-exercise').click();
        cy.wait('@participateInExerciseQuery');
    }

    openRunningProgrammingExercise(exerciseName: string) {
        cy.intercept(GET, BASE_API + 'programming-exercise-participations/*/student-participation-with-latest-result-and-feedbacks').as('initialQuery');
        this.getExerciseCardRootElement(exerciseName).find('[buttonicon="folder-open"]').click();
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
