import { Exam } from 'app/entities/exam.model';
import { artemis } from '../../ArtemisTesting';
import multipleChoiceTemplate from '../../../fixtures/quiz_exercise_fixtures/multipleChoiceQuiz_template.json';
import { BASE_API, EXERCISE_TYPE, PUT } from '../../constants';
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

    addGroupWithExercise(exam: Exam, title: string, exerciseType: EXERCISE_TYPE, processResponse: (data: any) => void) {
        const courseManagementRequests = artemis.requests.courseManagement;
        courseManagementRequests.addExerciseGroupForExam(exam).then((groupResponse) => {
            switch (exerciseType) {
                case EXERCISE_TYPE.Text:
                    courseManagementRequests.createTextExercise({ exerciseGroup: groupResponse.body }, title).then((response) => {
                        processResponse(response);
                    });
                    break;
                case EXERCISE_TYPE.Modeling:
                    courseManagementRequests.createModelingExercise({ exerciseGroup: groupResponse.body }, title).then((response) => {
                        processResponse(response);
                    });
                    break;
                case EXERCISE_TYPE.Quiz:
                    courseManagementRequests.createQuizExercise({ exerciseGroup: groupResponse.body }, [multipleChoiceTemplate], title).then((response) => {
                        processResponse(response);
                    });
                    break;
                case EXERCISE_TYPE.Programming:
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
