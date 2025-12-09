import { Page } from '@playwright/test';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamAPIRequests } from '../../requests/ExamAPIRequests';
import { ExerciseAPIRequests } from '../../requests/ExerciseAPIRequests';
import multipleChoiceTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import { AdditionalData, ExerciseType, Exercise as PlaywrightExercise } from '../../constants';
import { generateUUID } from '../../utils';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Visibility } from 'app/programming/shared/entities/programming-exercise-test-case.model';

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

    async setMandatoryBox(checked: boolean) {
        if (checked) {
            await this.getMandatoryBoxLocator().check();
        } else {
            await this.getMandatoryBoxLocator().uncheck();
        }
    }

    async isMandatoryBoxShouldBeChecked() {
        await this.getMandatoryBoxLocator().isChecked();
    }

    private getMandatoryBoxLocator() {
        return this.page.locator('#isMandatory');
    }

    async clickSave(): Promise<ExerciseGroup> {
        const responsePromise = this.page.waitForResponse(`api/exam/courses/*/exams/*/exercise-groups`);
        await this.page.locator('#save-group').click();
        const response = await responsePromise;
        return response.json();
    }

    async update() {
        const responsePromise = this.page.waitForResponse(`api/exam/courses/*/exams/*/exercise-groups`);
        await this.page.locator('#save-group').click();
        await responsePromise;
    }

    async addGroupWithExercise(
        exam: Exam,
        exerciseType: ExerciseType,
        additionalData: AdditionalData = {},
        isMandatory?: boolean,
        exerciseTemplate?: any,
    ): Promise<PlaywrightExercise> {
        const response = await this.handleAddGroupWithExercise(exam, 'Exercise ' + generateUUID(), exerciseType, additionalData, isMandatory, exerciseTemplate);
        let exercise = Object.assign({}, response!, { additionalData });
        if (exerciseType == ExerciseType.QUIZ) {
            const quiz = response as QuizExercise;
            additionalData!.quizExerciseID = quiz.quizQuestions![0].id;
            exercise = Object.assign({}, quiz, { additionalData });
        }

        if (exerciseType === ExerciseType.PROGRAMMING) {
            const RETRY_NUMBER = 0;
            await this.exerciseAPIRequests.changeProgrammingExerciseTestVisibility(exercise, Visibility.Always, RETRY_NUMBER);
        }

        return exercise;
    }

    async handleAddGroupWithExercise(
        exam: Exam,
        title: string,
        exerciseType: ExerciseType,
        additionalData: AdditionalData,
        isMandatory?: boolean,
        exerciseTemplate?: any,
    ): Promise<Exercise | undefined> {
        const exerciseGroup = await this.examAPIRequests.addExerciseGroupForExam(exam, 'Group ' + generateUUID(), isMandatory);
        switch (exerciseType) {
            case ExerciseType.TEXT:
                return await this.exerciseAPIRequests.createTextExercise({ exerciseGroup }, title, exerciseTemplate);
            case ExerciseType.MODELING:
                return await this.exerciseAPIRequests.createModelingExercise({ exerciseGroup }, title);
            case ExerciseType.QUIZ:
                return await this.exerciseAPIRequests.createQuizExercise({ body: { exerciseGroup }, quizQuestions: [multipleChoiceTemplate], title });
            case ExerciseType.PROGRAMMING:
                return await this.exerciseAPIRequests.createProgrammingExercise({ exerciseGroup, title, assessmentType: additionalData.progExerciseAssessmentType });
        }
    }
}
