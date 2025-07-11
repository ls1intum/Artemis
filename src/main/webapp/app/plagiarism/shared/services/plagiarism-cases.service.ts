import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PlagiarismCase, PlagiarismCaseDTO } from 'app/plagiarism/shared/entities/PlagiarismCase';
import { PlagiarismStatus } from 'app/plagiarism/shared/entities/PlagiarismStatus';
import { PlagiarismComparison } from 'app/plagiarism/shared/entities/PlagiarismComparison';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import { PlagiarismCaseInfo } from 'app/plagiarism/shared/entities/PlagiarismCaseInfo';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';

export type EntityResponseType = HttpResponse<PlagiarismCase>;
export type EntityArrayResponseType = HttpResponse<PlagiarismCase[]>;
export type Comparison = PlagiarismComparison;

@Injectable({ providedIn: 'root' })
export class PlagiarismCasesService {
    private http = inject(HttpClient);
    private resourceUrl = 'api/plagiarism/courses';
    private resourceUrlExercises = 'api/plagiarism/exercises';

    /* Instructor */

    /**
     * Get all plagiarism cases for the instructor of the course with the given id
     * @param courseId id of the course
     */
    public getCoursePlagiarismCasesForScores(courseId: number): Observable<HttpResponse<PlagiarismCaseDTO[]>> {
        return this.http.get<PlagiarismCaseDTO[]>(`${this.resourceUrl}/${courseId}/plagiarism-cases/for-scores`, { observe: 'response' });
    }

    /**
     * Get all plagiarism cases for the instructor of the course with the given id
     * @param courseId id of the course
     */
    public getCoursePlagiarismCasesForInstructor(courseId: number): Observable<EntityArrayResponseType> {
        return this.http.get<PlagiarismCase[]>(`${this.resourceUrl}/${courseId}/plagiarism-cases/for-instructor`, { observe: 'response' });
    }

    /**
     * Get all plagiarism cases for the instructor of the exam with the given id
     * @param courseId id of the course
     * @param examId id of the exam
     */
    public getExamPlagiarismCasesForInstructor(courseId: number, examId: number): Observable<EntityArrayResponseType> {
        return this.http.get<PlagiarismCase[]>(`${this.resourceUrl}/${courseId}/exams/${examId}/plagiarism-cases/for-instructor`, { observe: 'response' });
    }

    /**
     * Get the plagiarism case with the given id for the instructor
     * @param courseId id of the course
     * @param plagiarismCaseId id of the plagiarismCase
     */
    public getPlagiarismCaseDetailForInstructor(courseId: number, plagiarismCaseId: number): Observable<EntityResponseType> {
        return this.http.get<PlagiarismCase>(`${this.resourceUrl}/${courseId}/plagiarism-cases/${plagiarismCaseId}/for-instructor`, { observe: 'response' });
    }

    /**
     *
     * @param courseId id of the course
     * @param plagiarismCaseId id of the plagiarismCase
     * @param plagiarismVerdict plagiarism case verdict to save including the verdict itself and optionally the message or the point deduction
     */
    public saveVerdict(
        courseId: number,
        plagiarismCaseId: number,
        plagiarismVerdict: { verdict: PlagiarismVerdict; verdictMessage?: string; verdictPointDeduction?: number },
    ): Observable<EntityResponseType> {
        return this.http.put<PlagiarismCase>(`${this.resourceUrl}/${courseId}/plagiarism-cases/${plagiarismCaseId}/verdict`, plagiarismVerdict, { observe: 'response' });
    }

    /* Student */

    /**
     * Get the plagiarism case info for the student for the given course and exercise
     * @param courseId id of the course
     * @param exerciseId id of the exercise
     */
    public getPlagiarismCaseInfoForStudent(courseId: number, exerciseId: number): Observable<HttpResponse<PlagiarismCaseInfo>> {
        return this.http.get<PlagiarismCaseInfo>(`${this.resourceUrl}/${courseId}/exercises/${exerciseId}/plagiarism-case`, { observe: 'response' });
    }

    /**
     * Get the plagiarism case infos for the student for the given course and exercise id list for the exercises that the student is allowed to access.
     * @param courseId id of the course
     * @param exerciseIds ids of the exercises
     */
    public getPlagiarismCaseInfosForStudent(courseId: number, exerciseIds: number[]): Observable<HttpResponse<{ [exerciseId: number]: PlagiarismCaseInfo }>> {
        let params = new HttpParams();
        for (const exerciseId of exerciseIds) {
            params = params.append('exerciseId', exerciseId);
        }
        return this.http.get<PlagiarismCaseInfo[]>(`${this.resourceUrl}/${courseId}/plagiarism-cases`, { params, observe: 'response' });
    }

    /**
     * Get the plagiarism case with the given id for the student
     * @param courseId id of the course
     * @param plagiarismCaseId id of the plagiarismCase
     */
    public getPlagiarismCaseDetailForStudent(courseId: number, plagiarismCaseId: number): Observable<EntityResponseType> {
        return this.http.get<PlagiarismCase>(`${this.resourceUrl}/${courseId}/plagiarism-cases/${plagiarismCaseId}/for-student`, { observe: 'response' });
    }

    /**
     * Get the plagiarism comparison with the given id
     * @param courseId
     * @param plagiarismComparisonId
     */
    public getPlagiarismComparisonForSplitView(courseId: number, plagiarismComparisonId: number): Observable<HttpResponse<Comparison>> {
        return this.http.get<Comparison>(`${this.resourceUrl}/${courseId}/plagiarism-comparisons/${plagiarismComparisonId}/for-split-view`, {
            observe: 'response',
        });
    }

    /**
     * Update the status of the plagiarism comparison with given id
     * @param courseId
     * @param plagiarismComparisonId
     * @param { PlagiarismStatus } status
     */
    public updatePlagiarismComparisonStatus(courseId: number, plagiarismComparisonId: number, status: PlagiarismStatus): Observable<HttpResponse<void>> {
        return this.http.put<void>(`${this.resourceUrl}/${courseId}/plagiarism-comparisons/${plagiarismComparisonId}/status`, { status }, { observe: 'response' });
    }

    /**
     * Clean up plagiarism results and comparisons
     * If deleteAll is set to true, all plagiarism results belonging to the exercise are deleted,
     * otherwise only plagiarism comparisons or with status DENIED or CONFIRMED are deleted and old results are deleted as well.
     *
     * @param exerciseId
     * @param plagiarismResultId
     * @param deleteAll
     */
    public cleanUpPlagiarism(exerciseId: number, plagiarismResultId: number, deleteAll = false): Observable<HttpResponse<void>> {
        const params = new HttpParams().append('deleteAll', deleteAll ? 'true' : 'false');
        return this.http.delete<void>(`${this.resourceUrlExercises}/${exerciseId}/plagiarism-results/${plagiarismResultId}/plagiarism-comparisons`, {
            params,
            observe: 'response',
        });
    }
    public getNumberOfPlagiarismCasesForExercise(exercise: Exercise): Observable<number> {
        let courseId: number;
        if (exercise.exerciseGroup) {
            courseId = exercise.exerciseGroup.exam!.course!.id!;
        } else {
            courseId = exercise.course!.id!;
        }
        const exerciseId = exercise!.id;
        return this.http.get<number>(`${this.resourceUrl}/${courseId}/exercises/${exerciseId}/plagiarism-cases-count`);
    }
}
