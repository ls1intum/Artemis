import { Exam } from 'app/entities/exam.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { artemis } from '../../ArtemisTesting';
import multipleChoiceTemplate from '../../../fixtures/quiz_exercise_fixtures/multipleChoiceQuiz_template.json';
import { BASE_API, PUT } from '../../constants';
import { POST } from '../../constants';

/**
 * A class which encapsulates UI selectors and actions for the exam exercise group creation page.
 */
export class ExamExerciseGroupCreationPage {
    typeTitle(title: string) {
        cy.get('#title').clear().type(title);
    }

    isMandatoryBoxShouldBeChecked() {
        cy.get('#isMandatory').should('be.checked');
    }

    clickSave() {
        cy.intercept({ method: POST, url: `${BASE_API}courses/*/exams/*/exerciseGroups` }).as('createExerciseGroup');
        cy.get('#save-group').click();
        cy.wait('@createExerciseGroup');
    }

    update() {
        cy.intercept({ method: PUT, url: `${BASE_API}courses/*/exams/*/exerciseGroups` }).as('updateExerciseGroup');
        cy.get('#save-group').click();
        cy.wait('@updateExerciseGroup');
    }

    addGroupWithExercise(exam: Exam, title: string, exerciseType: ExerciseType, processResponse: (data: any) => void) {
        const courseManagementRequests = artemis.requests.courseManagement;
        courseManagementRequests.addExerciseGroupForExam(exam).then((groupResponse) => {
            switch (exerciseType) {
                case ExerciseType.TEXT:
                    courseManagementRequests.createTextExercise({ exerciseGroup: groupResponse.body }, title).then((response) => {
                        processResponse(response);
                    });
                    break;
                case ExerciseType.MODELING:
                    courseManagementRequests.createModelingExercise({ exerciseGroup: groupResponse.body }, title).then((response) => {
                        processResponse(response);
                    });
                    break;
                case ExerciseType.QUIZ:
                    courseManagementRequests.createQuizExercise({ exerciseGroup: groupResponse.body }, [multipleChoiceTemplate], title).then((response) => {
                        processResponse(response);
                    });
                    break;
                case ExerciseType.PROGRAMMING:
                    courseManagementRequests
                        .createProgrammingExercise({ exerciseGroup: groupResponse.body }, undefined, false, undefined, undefined, title, undefined, 'de.test')
                        .then((response) => {
                            processResponse(response);
                        });
                    break;
            }
        });
    }
}
