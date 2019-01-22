import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';

import { QuizExercise } from './quiz-exercise.model';
import { ExerciseService } from 'app/entities/exercise';
import { Question } from 'app/entities/question';

export type EntityResponseType = HttpResponse<QuizExercise>;
export type EntityArrayResponseType = HttpResponse<QuizExercise[]>;

@Injectable({ providedIn: 'root' })
export class QuizExerciseService {
    private resourceUrl = SERVER_API_URL + 'api/quiz-exercises';

    constructor(private http: HttpClient, private exerciseService: ExerciseService) {}

    create(quizExercise: QuizExercise): Observable<EntityResponseType> {
        const copy = this.exerciseService.convertDateFromClient(quizExercise);
        return this.http
            .post<QuizExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res));
    }

    update(quizExercise: QuizExercise): Observable<EntityResponseType> {
        const copy = this.exerciseService.convertDateFromClient(quizExercise);
        return this.http
            .put<QuizExercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<QuizExercise>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res));
    }

    recalculate(id: number): Observable<EntityResponseType> {
        return this.http
            .get<QuizExercise>(`${this.resourceUrl}/${id}/recalculate-statistics`, { observe: 'response' })
            .map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res));
    }

    /**
     * Note: the exercises in the response do not contain participations and do not contain the course to save network bandwidth
     * They also do not contain questions
     *
     * @param courseId
     */
    findForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<QuizExercise[]>(`api/courses/${courseId}/quiz-exercises`, { observe: 'response' })
            .map((res: EntityArrayResponseType) => this.exerciseService.convertDateArrayFromServer(res));
    }

    openForPractice(id: number): Observable<HttpResponse<string>> {
        return this.http.post<HttpResponse<string>>(`${this.resourceUrl}/${id}/open-for-practice`, { observe: 'response' });
    }

    findForStudent(id: number): Observable<EntityResponseType> {
        return this.http
            .get<QuizExercise>(`${this.resourceUrl}/${id}/for-student`, { observe: 'response' })
            .map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res));
    }

    start(id: number): Observable<HttpResponse<string>> {
        return this.http.post<HttpResponse<string>>(`${this.resourceUrl}/${id}/start-now`, { observe: 'response' });
    }

    setVisible(id: number): Observable<HttpResponse<string>> {
        return this.http.post<HttpResponse<string>>(`${this.resourceUrl}/${id}/set-visible`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        return this.http
            .get<QuizExercise[]>(this.resourceUrl, { observe: 'response' })
            .map((res: EntityArrayResponseType) => this.exerciseService.convertDateArrayFromServer(res));
    }

    delete(id: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    reset(id: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${SERVER_API_URL + 'api/exercises'}/${id}/reset`, { observe: 'response' });
    }

    /**
     * Exports given quiz questions into json file
     * @param quizQuestions Quiz questions we want to export
     * @param exportAll If true exports all questions, else exports only those whose export flag is true
     */
    exportQuiz(quizQuestions: Question[], exportAll: boolean) {
        // Make list of questions which we need to export,
        const questions: Question[] = [];
        for (const question of quizQuestions) {
            if (exportAll === true || question.exportQuiz === true) {
                delete question.questionStatistic;
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
        if (window.navigator.msSaveOrOpenBlob) {
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
}
