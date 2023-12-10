import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { createRequestOption } from 'app/shared/util/request.util';
import { QuizPool } from 'app/entities/quiz/quiz-pool.model';
import { objectToJsonBlob } from 'app/utils/blob-util';

export type EntityResponseType = HttpResponse<QuizExercise>;
export type EntityArrayResponseType = HttpResponse<QuizExercise[]>;

@Injectable({ providedIn: 'root' })
export class QuizPoolService {
    constructor(private http: HttpClient) {}

    /**
     * Update the given quiz pool that belongs to the given course id and exam id
     *
     * @param courseId the course id of which the exam belongs to
     * @param examId the exam id of which the quiz pool belongs to
     * @param quizPool the quiz pool to be updated
     * @param files the files to be uploaded
     * @param req request options
     * @return the updated quiz pool
     */
    update(courseId: number, examId: number, quizPool: QuizPool, files: Map<string, Blob>, req?: any): Observable<HttpResponse<QuizPool>> {
        const options = createRequestOption(req);
        const formData = new FormData();
        formData.append('quizPool', objectToJsonBlob(quizPool));
        files.forEach((file, fileName) => {
            formData.append('files', file, fileName);
        });
        return this.http.put<QuizPool>(`api/courses/${courseId}/exams/${examId}/quiz-pools`, formData, { params: options, observe: 'response' });
    }

    /**
     * Find the quiz pool that belongs to the given course id and exam id
     *
     * @param courseId the course id of which the exam belongs to
     * @param examId the exam id of which the quiz pool belongs to
     * @return the quiz pool that belongs to the given course id and exam id
     */
    find(courseId: number, examId: number): Observable<HttpResponse<QuizPool>> {
        return this.http.get<QuizPool>(`api/courses/${courseId}/exams/${examId}/quiz-pools`, { observe: 'response' });
    }
}
