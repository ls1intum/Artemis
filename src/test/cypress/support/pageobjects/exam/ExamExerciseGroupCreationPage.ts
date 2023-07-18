import { Exam } from 'app/entities/exam.model';
import { courseManagementRequest } from '../../artemis';
import multipleChoiceTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import { BASE_API, EXERCISE_TYPE, PUT } from '../../constants';
import { POST } from '../../constants';
import { AdditionalData, Exercise } from './ExamParticipation';
import { generateUUID } from '../../utils';

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
        return cy.wait('@createExerciseGroup');
    }

    update() {
        cy.intercept({ method: PUT, url: `${BASE_API}courses/*/exams/*/exerciseGroups` }).as('updateExerciseGroup');
        cy.get('#save-group').click();
        cy.wait('@updateExerciseGroup');
    }

    addGroupWithExercise(exam: Exam, exerciseType: EXERCISE_TYPE, additionalData: AdditionalData = {}): Promise<Exercise> {
        return new Promise((resolve) => {
            this.handleAddGroupWithExercise(exam, 'Exercise ' + generateUUID(), exerciseType, additionalData, (response) => {
                if (exerciseType == EXERCISE_TYPE.Quiz) {
                    additionalData!.quizExerciseID = response.body.quizQuestions![0].id;
                }
                const exercise = { ...response.body, additionalData };
                resolve(exercise);
            });
        });
    }

    handleAddGroupWithExercise(exam: Exam, title: string, exerciseType: EXERCISE_TYPE, additionalData: AdditionalData, processResponse: (data: any) => void) {
        courseManagementRequest.addExerciseGroupForExam(exam).then((groupResponse) => {
            switch (exerciseType) {
                case EXERCISE_TYPE.Text:
                    courseManagementRequest.createTextExercise({ exerciseGroup: groupResponse.body }, title).then((response) => {
                        processResponse(response);
                    });
                    break;
                case EXERCISE_TYPE.Modeling:
                    courseManagementRequest.createModelingExercise({ exerciseGroup: groupResponse.body }, title).then((response) => {
                        processResponse(response);
                    });
                    break;
                case EXERCISE_TYPE.Quiz:
                    courseManagementRequest.createQuizExercise({ exerciseGroup: groupResponse.body }, [multipleChoiceTemplate], title).then((response) => {
                        processResponse(response);
                    });
                    break;
                case EXERCISE_TYPE.Programming:
                    courseManagementRequest
                        .createProgrammingExercise(
                            { exerciseGroup: groupResponse.body },
                            undefined,
                            false,
                            undefined,
                            undefined,
                            title,
                            undefined,
                            undefined,
                            undefined,
                            undefined,
                            additionalData.progExerciseAssessmentType,
                        )
                        .then((response) => {
                            processResponse(response);
                        });
                    break;
            }
        });
    }
}
