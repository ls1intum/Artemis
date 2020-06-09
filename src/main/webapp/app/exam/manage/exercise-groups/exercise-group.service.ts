import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SERVER_API_URL } from 'app/app.constants';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exam } from 'app/entities/exam.model';

type EntityResponseType = HttpResponse<ExerciseGroup>;
type EntityArrayResponseType = HttpResponse<ExerciseGroup[]>;

@Injectable({ providedIn: 'root' })
export class ExerciseGroupService {
    public resourceUrlExerciseGroups = SERVER_API_URL + 'api/exerciseGroups';
    public resourceUrlExams = SERVER_API_URL + 'api/exams';

    constructor(private router: Router, private http: HttpClient) {}

    /**
     * Create an exercise group on the server using a POST request.
     * @param examId The exam id.
     * @param exerciseGroup The exercise group to create.
     */
    create(examId: number, exerciseGroup: ExerciseGroup): Observable<EntityResponseType> {
        return this.http.post<ExerciseGroup>(`${this.resourceUrlExams}/${examId}/exerciseGroups`, exerciseGroup, { observe: 'response' });
    }

    /**
     * Update an exercise group on the server using a PUT request.
     * @param examId The course id.
     * @param exerciseGroup The exercise group to update.
     */
    update(examId: number, exerciseGroup: ExerciseGroup): Observable<EntityResponseType> {
        return this.http.put<ExerciseGroup>(`${this.resourceUrlExams}/${examId}/exerciseGroups`, exerciseGroup, { observe: 'response' });
    }

    /**
     * Delete an exercise group on the server using a DELETE request.
     * @param id The id of the exercise group to delete.
     */
    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrlExerciseGroups}/${id}`, { observe: 'response' });
    }

    /**
     * Find all exercise groups for the given exam.
     * @param examId The exam id.
     */
    findAllForExam(examId: number): Observable<EntityArrayResponseType> {
        return this.http.get<ExerciseGroup[]>(`${this.resourceUrlExams}/${examId}/exerciseGroups`, { observe: 'response' });
    }
}
