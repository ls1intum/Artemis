import { Exam } from 'app/entities/exam.model';

import multipleChoiceTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import { examAPIRequests, exerciseAPIRequest } from '../../artemis';
import { AdditionalData, COURSE_BASE, Exercise, ExerciseType, PUT } from '../../constants';
import { POST } from '../../constants';
import { convertModelAfterMultiPart, generateUUID } from '../../utils';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

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
        cy.intercept({ method: POST, url: `${COURSE_BASE}/*/exams/*/exerciseGroups` }).as('createExerciseGroup');
        cy.get('#save-group').click();
        return cy.wait('@createExerciseGroup');
    }

    update() {
        cy.intercept({ method: PUT, url: `${COURSE_BASE}/*/exams/*/exerciseGroups` }).as('updateExerciseGroup');
        cy.get('#save-group').click();
        cy.wait('@updateExerciseGroup');
    }

    addGroupWithExercise(exam: Exam, exerciseType: ExerciseType, additionalData: AdditionalData = {}): Promise<Exercise> {
        return new Promise((resolve) => {
            this.handleAddGroupWithExercise(exam, 'Exercise ' + generateUUID(), exerciseType, additionalData, (response) => {
                let exercise = { ...response.body, additionalData };
                if (exerciseType == ExerciseType.QUIZ) {
                    const quiz = convertModelAfterMultiPart(response) as QuizExercise;
                    additionalData!.quizExerciseID = quiz.quizQuestions![0].id;
                    exercise = { ...quiz, additionalData };
                }
                resolve(exercise);
            });
        });
    }

    handleAddGroupWithExercise(exam: Exam, title: string, exerciseType: ExerciseType, additionalData: AdditionalData, processResponse: (data: any) => void) {
        examAPIRequests.addExerciseGroupForExam(exam).then((groupResponse) => {
            switch (exerciseType) {
                case ExerciseType.TEXT:
                    exerciseAPIRequest.createTextExercise({ exerciseGroup: groupResponse.body }, title).then((response) => {
                        processResponse(response);
                    });
                    break;
                case ExerciseType.MODELING:
                    exerciseAPIRequest.createModelingExercise({ exerciseGroup: groupResponse.body }, title).then((response) => {
                        processResponse(response);
                    });
                    break;
                case ExerciseType.QUIZ:
                    exerciseAPIRequest.createQuizExercise({ exerciseGroup: groupResponse.body }, [multipleChoiceTemplate], title).then((response) => {
                        processResponse(response);
                    });
                    break;
                case ExerciseType.PROGRAMMING:
                    exerciseAPIRequest
                        .createProgrammingExercise({ exerciseGroup: groupResponse.body, title, assessmentType: additionalData.progExerciseAssessmentType })
                        .then((response) => {
                            processResponse(response);
                        });
                    break;
            }
        });
    }
}
