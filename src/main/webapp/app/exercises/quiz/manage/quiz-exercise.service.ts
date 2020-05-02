import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { map } from 'rxjs/operators';
import { SERVER_API_URL } from 'app/app.constants';

import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { createRequestOption } from 'app/shared/util/request-util';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';

export type EntityResponseType = HttpResponse<QuizExercise>;
export type EntityArrayResponseType = HttpResponse<QuizExercise[]>;

@Injectable({ providedIn: 'root' })
export class QuizExerciseService {
    private resourceUrl = SERVER_API_URL + 'api/quiz-exercises';

    QuizStatus = {
        HIDDEN: 'Hidden',
        VISIBLE: 'Visible',
        ACTIVE: 'Active',
        CLOSED: 'Closed',
        OPEN_FOR_PRACTICE: 'Open for Practice',
    };

    constructor(private http: HttpClient, private exerciseService: ExerciseService) {}

    /**
     * Create the given quiz exercise
     * @param quizExercise the quiz exercise that should be created
     */
    create(quizExercise: QuizExercise): Observable<EntityResponseType> {
        const copy = this.exerciseService.convertDateFromClient(quizExercise);
        return this.http
            .post<QuizExercise>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)));
    }

    /**
     * Update the given quiz exercise
     * @param quizExercise the quiz exercise that should be updated
     * @param req Additional parameters that should be pased to the server when updating the exercise
     */
    update(quizExercise: QuizExercise, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        const copy = this.exerciseService.convertDateFromClient(quizExercise);
        return this.http
            .put<QuizExercise>(this.resourceUrl, copy, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)));
    }

    /**
     * Find the quiz exercise with the given id
     * @param quizExerciseId the id of the quiz exercise that should be found
     */
    find(quizExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<QuizExercise>(`${this.resourceUrl}/${quizExerciseId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)));
    }

    /**
     * Recalculate the statistics for a given quiz exercise
     * @param quizExerciseId the id of the quiz exercise for which the statistics should be recalculated
     */
    recalculate(quizExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<QuizExercise>(`${this.resourceUrl}/${quizExerciseId}/recalculate-statistics`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)));
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
            .pipe(map((res: EntityArrayResponseType) => this.exerciseService.convertDateArrayFromServer(res)));
    }

    /**
     * Find the quiz exercise with the given id, with information filtered for students
     * @param quizExerciseId the id of the quiz exercise that should be loaded
     */
    findForStudent(quizExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<QuizExercise>(`${this.resourceUrl}/${quizExerciseId}/for-student`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)));
    }

    /**
     * Open a quiz exercise for practice
     * @param quizExerciseId the id of the quiz exercise that should be opened for practice
     */
    openForPractice(quizExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .put<QuizExercise>(`${this.resourceUrl}/${quizExerciseId}/open-for-practice`, null, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)));
    }

    /**
     * Start a quiz exercise
     * @param quizExerciseId the id of the quiz exercise that should be started
     */
    start(quizExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .put<QuizExercise>(`${this.resourceUrl}/${quizExerciseId}/start-now`, null, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)));
    }

    /**
     * Set a quiz exercise visible
     * @param quizExerciseId the id of the quiz exercise that should be set visible
     */
    setVisible(quizExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .put<QuizExercise>(`${this.resourceUrl}/${quizExerciseId}/set-visible`, null, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)));
    }

    /**
     * Load all quiz exercises
     */
    query(): Observable<EntityArrayResponseType> {
        return this.http
            .get<QuizExercise[]>(this.resourceUrl, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.exerciseService.convertDateArrayFromServer(res)));
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
     * @param quizExerciseId the id of the quiz exercise that should be resetted
     */
    reset(quizExerciseId: number): Observable<HttpResponse<{}>> {
        return this.http.delete(`${SERVER_API_URL + 'api/exercises'}/${quizExerciseId}/reset`, { observe: 'response' });
    }

    /**
     * Exports given quiz questions into json file
     * @param quizQuestions Quiz questions we want to export
     * @param exportAll If true exports all questions, else exports only those whose export flag is true
     */
    exportQuiz(quizQuestions: QuizQuestion[], exportAll: boolean) {
        // Make list of questions which we need to export,
        const questions: QuizQuestion[] = [];
        for (const question of quizQuestions) {
            if (exportAll || question.exportQuiz) {
                delete question.quizQuestionStatistic;
                delete question.exercise;
                questions.push(question);
            }
        }
        if (questions.length === 0) {
            return;
        }
        // Make blob from the list of questions and download the file,
        const quizJson = JSON.stringify(questions);
        const blob = new Blob([quizJson], { type: 'application/json' });
        this.downloadFile(blob);
    }

    /**
     * Make a file of given blob and allows user to download it from the browser.
     * @param blob data to be written in file.
     */
    downloadFile(blob: Blob) {
        // Different browsers require different code to download file,
        if (window.navigator.appVersion.toString().indexOf('.NET') > 0) {
            // IE & Edge
            window.navigator.msSaveBlob(blob, 'quiz.json');
        } else {
            // Chrome & FF
            // Create a url and attach file to it,
            const url = window.URL.createObjectURL(blob);
            const anchor = document.createElement('a');
            anchor.href = url;
            anchor.download = 'quiz.json';
            document.body.appendChild(anchor); // For FF
            // Click the url so that browser shows save file dialog,
            anchor.click();
            document.body.removeChild(anchor);
        }
    }
    /**
     * Start the given quiz-exercise immediately
     *
     * @param quizExercise the quiz exercise id to start
     */
    statusForQuiz(quizExercise: QuizExercise) {
        if (quizExercise.isPlannedToStart && quizExercise.remainingTime != null) {
            if (quizExercise.remainingTime <= 0) {
                // the quiz is over
                return quizExercise.isOpenForPractice ? this.QuizStatus.OPEN_FOR_PRACTICE : this.QuizStatus.CLOSED;
            } else {
                return this.QuizStatus.ACTIVE;
            }
        }
        // the quiz hasn't started yet
        return quizExercise.isVisibleBeforeStart ? this.QuizStatus.VISIBLE : this.QuizStatus.HIDDEN;
    }
}
