import dayjs from 'dayjs';
import { Page } from 'playwright-core';

import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';

import textExerciseTemplate from '../../fixtures/exercise/text/template.json';
import quizExerciseTemplate from '../../fixtures/exercise/quiz/template.json';
import modelingExerciseTemplate from '../../fixtures/exercise/modeling/template.json';
import { MODELING_EXERCISE_BASE, QUIZ_EXERCISE_BASE, TEXT_EXERCISE_BASE } from '../constants';
import { dayjsToString, generateUUID, titleLowercase } from '../utils';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';

export class ExerciseAPIRequests {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Adds a text exercise to an exercise group in an exam or to a course.
     *
     * @param body - An object containing either the course or exercise group the exercise will be added to.
     * @param title - The title for the text exercise (optional, default: auto-generated).
     * @returns Promise<TextExercise> representing the text exercise created.
     */
    async createTextExercise(body: { course: Course } | { exerciseGroup: ExerciseGroup }, title = 'Text ' + generateUUID()): Promise<TextExercise> {
        const template = {
            ...textExerciseTemplate,
            title,
            channelName: 'exercise-' + titleLowercase(title),
        };
        const textExercise = Object.assign({}, template, body);
        const response = await this.page.request.post(TEXT_EXERCISE_BASE, { data: textExercise });
        return response.json();
    }

    /**
     * Deletes a text exercise with the specified exercise ID.
     *
     * @param exerciseId - The ID of the text exercise to be deleted.
     */
    async deleteTextExercise(exerciseId: number) {
        await this.page.request.delete(TEXT_EXERCISE_BASE + exerciseId);
    }

    /**
     * Creates a quiz exercise.
     *
     * @param body - An object containing either the course or exercise group the exercise will be added to.
     * @param quizQuestions - A list of quiz question objects that make up the quiz (e.g., multiple choice, short answer, or drag and drop).
     * @param title - The title for the quiz exercise (optional, default: auto-generated).
     * @param releaseDate - The release date of the quiz exercise (optional, default: current date + 1 year).
     * @param duration - The duration in seconds that students get to complete the quiz (optional, default: 600 seconds).
     * @returns Promise<QuizExercise> representing the quiz exercise created.
     */
    async createQuizExercise(
        body: { course: Course } | { exerciseGroup: ExerciseGroup },
        quizQuestions: any[],
        title = 'Quiz ' + generateUUID(),
        releaseDate = dayjs().add(1, 'year'),
        duration = 600,
    ): Promise<QuizExercise> {
        const quizExercise: any = {
            ...quizExerciseTemplate,
            title,
            quizQuestions,
            duration,
            channelName: 'exercise-' + titleLowercase(title),
        };
        const dates = {
            releaseDate: dayjsToString(releaseDate),
        };

        // eslint-disable-next-line no-prototype-builtins
        const newQuizExercise = body.hasOwnProperty('course') ? { ...quizExercise, ...dates, ...body } : { ...quizExercise, ...body };
        const multipartData = {
            exercise: {
                name: 'exercise',
                mimeType: 'application/json',
                buffer: Buffer.from(JSON.stringify(newQuizExercise)),
            },
        };

        const response = await this.page.request.post(QUIZ_EXERCISE_BASE, {
            multipart: multipartData,
        });
        return response.json();
    }

    /**
     * Deletes a quiz exercise with the specified exercise ID.
     *
     * @param exerciseId - The ID of the quiz exercise to be deleted.
     */
    async deleteQuizExercise(exerciseId: number) {
        await this.page.request.delete(QUIZ_EXERCISE_BASE + exerciseId);
    }

    /**
     * Creates a modeling exercise.
     *
     * @param body - An object containing either the course or exercise group the exercise will be added to.
     * @param title - The title for the exercise (optional, default: auto-generated).
     * @param releaseDate - The release date of the exercise (optional, default: current date).
     * @param dueDate - The due date of the exercise (optional, default: current date + 1 day).
     * @param assessmentDueDate - The assessment due date of the exercise (optional, default: current date + 2 days).
     * @returns A Cypress.Chainable<Cypress.Response<ModelingExercise>> representing the API request response.
     */
    async createModelingExercise(
        body: { course: Course } | { exerciseGroup: ExerciseGroup },
        title = 'Modeling ' + generateUUID(),
        releaseDate = dayjs(),
        dueDate = dayjs().add(1, 'days'),
        assessmentDueDate = dayjs().add(2, 'days'),
    ): Promise<ModelingExercise> {
        const templateCopy = {
            ...modelingExerciseTemplate,
            title,
            channelName: 'exercise-' + titleLowercase(title),
        };
        const dates = {
            releaseDate: dayjsToString(releaseDate),
            dueDate: dayjsToString(dueDate),
            assessmentDueDate: dayjsToString(assessmentDueDate),
        };
        let newModelingExercise;
        // eslint-disable-next-line no-prototype-builtins
        if (body.hasOwnProperty('course')) {
            newModelingExercise = Object.assign({}, templateCopy, dates, body);
        } else {
            newModelingExercise = Object.assign({}, templateCopy, body);
        }
        const response = await this.page.request.post(MODELING_EXERCISE_BASE, { data: newModelingExercise });
        return response.json();
    }
}
