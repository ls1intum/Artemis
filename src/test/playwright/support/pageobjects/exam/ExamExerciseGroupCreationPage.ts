import { Page } from '@playwright/test';
import { Exam } from 'app/entities/exam.model';
import { ExamAPIRequests } from '../../requests/ExamAPIRequests';
import { ExerciseAPIRequests } from '../../requests/ExerciseAPIRequests';
import multipleChoiceTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import { AdditionalData, BASE_API, Exercise, ExerciseType } from '../../constants';
import { generateUUID } from '../../utils';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';

export class ExamExerciseGroupCreationPage {
    private readonly page: Page;
    private readonly examAPIRequests: ExamAPIRequests;
    private readonly exerciseAPIRequests: ExerciseAPIRequests;

    constructor(page: Page, examAPIRequests: ExamAPIRequests, exerciseAPIRequests: ExerciseAPIRequests) {
        this.page = page;
        this.examAPIRequests = examAPIRequests;
        this.exerciseAPIRequests = exerciseAPIRequests;
    }

    async typeTitle(title: string) {
        const titleField = this.page.locator('#title');
        await titleField.clear();
        await titleField.fill(title);
    }

    async isMandatoryBoxShouldBeChecked() {
        await this.page.locator('#isMandatory').isChecked();
    }

    async clickSave(): Promise<ExerciseGroup> {
        const responsePromise = this.page.waitForResponse(`${BASE_API}courses/*/exams/*/exerciseGroups`);
        await this.page.locator('#save-group').click();
        const response = await responsePromise;
        return response.json();
    }

    async update() {
        const responsePromise = this.page.waitForResponse(`${BASE_API}courses/*/exams/*/exerciseGroups`);
        await this.page.locator('#save-group').click();
        await responsePromise;
    }

    async addGroupWithExercise(exam: Exam, exerciseType: ExerciseType, additionalData: AdditionalData = {}): Promise<Exercise> {
        const response = await this.handleAddGroupWithExercise(exam, 'Exercise ' + generateUUID(), exerciseType, additionalData);
        let exercise = { ...response! };
        if (exerciseType == ExerciseType.QUIZ) {
            const quiz = response as QuizExercise;
            additionalData!.quizExerciseID = quiz.quizQuestions![0].id;
            exercise = { ...quiz, additionalData };
        }
        return exercise;
    }

    async handleAddGroupWithExercise(exam: Exam, title: string, exerciseType: ExerciseType, additionalData: AdditionalData): Promise<any> {
        const exerciseGroup = await this.examAPIRequests.addExerciseGroupForExam(exam);
        switch (exerciseType) {
            case ExerciseType.TEXT:
                return await this.exerciseAPIRequests.createTextExercise({ exerciseGroup }, title);
            case ExerciseType.MODELING:
                return await this.exerciseAPIRequests.createModelingExercise({ exerciseGroup }, title);
            case ExerciseType.QUIZ:
                return await this.exerciseAPIRequests.createQuizExercise({ exerciseGroup }, [multipleChoiceTemplate], title);
            case ExerciseType.PROGRAMMING:
                return await this.exerciseAPIRequests.createProgrammingExercise({ exerciseGroup, title, assessmentType: additionalData.progExerciseAssessmentType });
        }
    }
}
