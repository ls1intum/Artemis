import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { QuizBatch, QuizExercise, QuizStatus } from 'app/entities/quiz/quiz-exercise.model';
import { createRequestOption } from 'app/shared/util/request.util';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { downloadFile } from 'app/shared/util/download.util';

export type EntityResponseType = HttpResponse<QuizExercise>;
export type EntityArrayResponseType = HttpResponse<QuizExercise[]>;

@Injectable({ providedIn: 'root' })
export class QuizExerciseService {
    private resourceUrl = SERVER_API_URL + 'api/quiz-exercises';

    constructor(private http: HttpClient, private exerciseService: ExerciseService) {}

    /**
     * Create the given quiz exercise
     * @param quizExercise the quiz exercise that should be created
     */
    create(quizExercise: QuizExercise): Observable<EntityResponseType> {
        const copy = ExerciseService.convertExerciseDatesFromClient(quizExercise);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .post<QuizExercise>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Imports a quiz exercise by cloning the entity itself plus example solutions and example submissions
     *
     * @param adaptedSourceQuizExercise The exercise that should be imported, including adapted values for the
     * new exercise. E.g. with another title than the original exercise. Old values that should get discarded
     * (like the old ID) will be handled by the server.
     */
    import(adaptedSourceQuizExercise: QuizExercise) {
        let copy = ExerciseService.convertExerciseDatesFromClient(adaptedSourceQuizExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .post<QuizExercise>(`${this.resourceUrl}/import/${adaptedSourceQuizExercise.id}`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Update the given quiz exercise
     * @param quizExercise the quiz exercise that should be updated
     * @param req Additional parameters that should be passed to the server when updating the exercise
     */
    update(quizExercise: QuizExercise, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        const copy = ExerciseService.convertExerciseDatesFromClient(quizExercise);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .put<QuizExercise>(this.resourceUrl, copy, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Find the quiz exercise with the given id
     * @param quizExerciseId the id of the quiz exercise that should be found
     */
    find(quizExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<QuizExercise>(`${this.resourceUrl}/${quizExerciseId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Recalculate the statistics for a given quiz exercise
     * @param quizExerciseId the id of the quiz exercise for which the statistics should be recalculated
     */
    recalculate(quizExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<QuizExercise>(`${this.resourceUrl}/${quizExerciseId}/recalculate-statistics`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Note: the exercises in the response do not contain participations and do not contain the course to save network bandwidth
     * They also do not contain questions
     *
     * @param courseId the course for which the quiz exercises should be returned
     */
    findForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<QuizExercise[]>(`api/courses/${courseId}/quiz-exercises`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.exerciseService.processExerciseEntityArrayResponse(res)));
    }

    /**
     * Note: the exercises in the response do not contain participations, the course and also not the exerciseGroup to save network bandwidth
     * They also do not contain questions
     *
     * @param examId the exam for which the quiz exercises should be returned
     */
    findForExam(examId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<QuizExercise[]>(`api/${examId}/quiz-exercises`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.exerciseService.processExerciseEntityArrayResponse(res)));
    }

    /**
     * Find the quiz exercise with the given id, with information filtered for students
     * @param quizExerciseId the id of the quiz exercise that should be loaded
     */
    findForStudent(quizExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<QuizExercise>(`${this.resourceUrl}/${quizExerciseId}/for-student`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Open a quiz exercise for practice
     * @param quizExerciseId the id of the quiz exercise that should be opened for practice
     */
    openForPractice(quizExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .put<QuizExercise>(`${this.resourceUrl}/${quizExerciseId}/open-for-practice`, null, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Start a quiz exercise
     * @param quizExerciseId the id of the quiz exercise that should be started
     */
    start(quizExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .put<QuizExercise>(`${this.resourceUrl}/${quizExerciseId}/start-now`, null, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * End a quiz exercise
     * @param quizExerciseId the id of the quiz exercise that should be stopped
     */
    end(quizExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .put<QuizExercise>(`${this.resourceUrl}/${quizExerciseId}/end-now`, null, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Set a quiz exercise visible
     * @param quizExerciseId the id of the quiz exercise that should be set visible
     */
    setVisible(quizExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .put<QuizExercise>(`${this.resourceUrl}/${quizExerciseId}/set-visible`, null, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Start a quiz batch
     * @param quizBatchId the id of the quiz batch that should be started
     */
    startBatch(quizBatchId: number): Observable<HttpResponse<QuizBatch>> {
        return this.http.put<QuizBatch>(`${this.resourceUrl}/${quizBatchId}/start-batch`, null, { observe: 'response' });
    }

    /**
     * Start a quiz batch
     * @param quizExerciseId the id of the quiz exercise that should be started
     */
    addBatch(quizExerciseId: number): Observable<HttpResponse<QuizBatch>> {
        return this.http.put<QuizBatch>(`${this.resourceUrl}/${quizExerciseId}/add-batch`, null, { observe: 'response' });
    }

    /**
     * Load all quiz exercises
     */
    query(): Observable<EntityArrayResponseType> {
        return this.http
            .get<QuizExercise[]>(this.resourceUrl, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.exerciseService.processExerciseEntityArrayResponse(res)));
    }

    /**
     * Delete a quiz exercise
     * @param quizExerciseId the id of the quiz exercise that should be deleted
     */
    delete(quizExerciseId: number): Observable<HttpResponse<{}>> {
        return this.http.delete(`${this.resourceUrl}/${quizExerciseId}`, { observe: 'response' });
    }

    /**
     * Reset a quiz exercise
     * @param quizExerciseId the id of the quiz exercise that should be reset
     */
    reset(quizExerciseId: number): Observable<HttpResponse<{}>> {
        return this.http.delete(`${SERVER_API_URL + 'api/exercises'}/${quizExerciseId}/reset`, { observe: 'response' });
    }

    join(quizExerciseId: number, password: string): Observable<HttpResponse<QuizBatch>> {
        return this.http.post<QuizExercise>(`${this.resourceUrl}/${quizExerciseId}/join`, { password }, { observe: 'response' });
    }

    /**
     * Exports given quiz questions into json file
     * @param quizQuestions Quiz questions we want to export
     * @param exportAll If true exports all questions, else exports only those whose export flag is true
     * @param fileName Name (without ending) of the resulting file, defaults to 'quiz'
     */
    exportQuiz(quizQuestions?: QuizQuestion[], exportAll?: boolean, fileName?: string) {
        // Make list of questions which we need to export,
        const questions: QuizQuestion[] = [];
        quizQuestions!.forEach((question) => {
            if (exportAll === true || question.exportQuiz) {
                question.quizQuestionStatistic = undefined;
                question.exercise = undefined;
                questions.push(question);
            }
        });
        if (questions.length === 0) {
            return;
        }
        // Make blob from the list of questions and download the file,
        const quizJson = JSON.stringify(questions);
        const blob = new Blob([quizJson], { type: 'application/json' });
        downloadFile(blob, (fileName ?? 'quiz') + '.json');
    }

    /**
     * Evaluates the QuizStatus for a given quiz
     *
     * @param quizExercise the quiz exercise to get the status of
     * @return the status of the quiz
     */
    getStatus(quizExercise: QuizExercise) {
        if (!quizExercise.quizStarted) {
            return QuizStatus.INVISIBLE;
        }
        if (quizExercise.quizEnded) {
            return quizExercise.isOpenForPractice ? QuizStatus.OPEN_FOR_PRACTICE : QuizStatus.CLOSED;
        }
        if (quizExercise.quizBatches && quizExercise.quizBatches.some((batch) => batch.started)) {
            return QuizStatus.ACTIVE;
        }
        return QuizStatus.VISIBLE;
    }
}
