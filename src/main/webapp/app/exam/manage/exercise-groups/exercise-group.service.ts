import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';

type EntityResponseType = HttpResponse<ExerciseGroup>;
type EntityArrayResponseType = HttpResponse<ExerciseGroup[]>;

@Injectable({ providedIn: 'root' })
export class ExerciseGroupService {
    private http = inject(HttpClient);

    public resourceUrl = 'api/exam/courses';

    /**
     * Create an exercise group on the server using a POST request.
     * @param courseId The course id.
     * @param examId The exam id.
     * @param exerciseGroup The exercise group to create.
     */
    create(courseId: number, examId: number, exerciseGroup: ExerciseGroup): Observable<EntityResponseType> {
        return this.http.post<ExerciseGroup>(`${this.resourceUrl}/${courseId}/exams/${examId}/exercise-groups`, exerciseGroup, { observe: 'response' });
    }

    /**
     * Update an exercise group on the server using a PUT request.
     * @param courseId The course id.
     * @param examId The exam id.
     * @param exerciseGroup The exercise group to update.
     */
    update(courseId: number, examId: number, exerciseGroup: ExerciseGroup): Observable<EntityResponseType> {
        return this.http.put<ExerciseGroup>(`${this.resourceUrl}/${courseId}/exams/${examId}/exercise-groups`, exerciseGroup, { observe: 'response' });
    }

    /**
     * Find an exercise group on the server using a GET request.
     * @param courseId The course id.
     * @param examId The exam id.
     * @param exerciseGroupId The id of the exercise group to get.
     */
    find(courseId: number, examId: number, exerciseGroupId: number): Observable<EntityResponseType> {
        return this.http.get<ExerciseGroup>(`${this.resourceUrl}/${courseId}/exams/${examId}/exercise-groups/${exerciseGroupId}`, { observe: 'response' });
    }

    /**
     * Delete an exercise group on the server using a DELETE request.
     * @param courseId The course id.
     * @param examId The exam id.
     * @param exerciseGroupId The id of the exercise group to delete.
     * @param deleteStudentReposBuildPlans indicates if the StudentReposBuildPlans should be also deleted or not
     * @param deleteBaseReposBuildPlans indicates if the BaseReposBuildPlans should be also deleted or not
     */
    delete(courseId: number, examId: number, exerciseGroupId: number, deleteStudentReposBuildPlans: boolean, deleteBaseReposBuildPlans: boolean): Observable<HttpResponse<void>> {
        let params = new HttpParams();
        if (deleteBaseReposBuildPlans != undefined && deleteStudentReposBuildPlans != undefined) {
            params = params.set('deleteStudentReposBuildPlans', deleteStudentReposBuildPlans.toString());
            params = params.set('deleteBaseReposBuildPlans', deleteBaseReposBuildPlans.toString());
        }
        return this.http.delete<void>(`${this.resourceUrl}/${courseId}/exams/${examId}/exercise-groups/${exerciseGroupId}`, { params, observe: 'response' });
    }

    /**
     * Find all exercise groups for the given exam.
     * @param courseId The course id.
     * @param examId The exam id.
     */
    findAllForExam(courseId: number, examId: number): Observable<EntityArrayResponseType> {
        return this.http.get<ExerciseGroup[]>(`${this.resourceUrl}/${courseId}/exams/${examId}/exercise-groups`, { observe: 'response' });
    }

    /**
     * Move an exam exercise into a different exercise group of the same exam. Rejected by the server (409) once any
     * student exam has been generated for the exam, since that would desync already-picked exercise selections and
     * the exam's cached point totals.
     * @param courseId The course id.
     * @param examId The exam id.
     * @param exerciseId The id of the exercise to move.
     * @param exerciseGroupId The id of the target exercise group.
     */
    moveExerciseToGroup(courseId: number, examId: number, exerciseId: number, exerciseGroupId: number): Observable<HttpResponse<void>> {
        return this.http.put<void>(`${this.resourceUrl}/${courseId}/exams/${examId}/exercises/${exerciseId}/exercise-group`, { exerciseGroupId }, { observe: 'response' });
    }
}
