import { Service, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { PlagiarismApi } from 'app/openapi/api/plagiarism-api';
import { PlagiarismCaseApi } from 'app/openapi/api/plagiarism-case-api';
import { PlagiarismVerdict as PlagiarismVerdictRequest } from 'app/openapi/model/plagiarism-verdict';
import { PlagiarismCase, PlagiarismCaseDTO, PlagiarismCaseVerdictResponse } from 'app/plagiarism/shared/entities/PlagiarismCase';
import { PlagiarismStatus } from 'app/plagiarism/shared/entities/PlagiarismStatus';
import { PlagiarismComparison } from 'app/plagiarism/shared/entities/PlagiarismComparison';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import { PlagiarismCaseInfo } from 'app/plagiarism/shared/entities/PlagiarismCaseInfo';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import {
    toPlagiarismCaseForScores,
    toPlagiarismCaseFromDetail,
    toPlagiarismCaseFromOverview,
    toPlagiarismCaseInfoMap,
    toPlagiarismCaseVerdictResponse,
    toPlagiarismComparison,
} from 'app/plagiarism/shared/services/plagiarism-api.mapper';

/**
 * Plagiarism cases and comparisons. Delegates the transport to the generated {@link PlagiarismCaseApi} and {@link PlagiarismApi} and maps their models to the
 * client models the views use.
 */
@Service()
export class PlagiarismCasesService {
    private plagiarismCaseApi = inject(PlagiarismCaseApi);
    private plagiarismApi = inject(PlagiarismApi);

    /* Instructor */

    /**
     * Get all plagiarism cases of the course with the given id for the course scores
     * @param courseId id of the course
     */
    public getCoursePlagiarismCasesForScores(courseId: number): Observable<PlagiarismCaseDTO[]> {
        return this.plagiarismCaseApi.getPlagiarismCasesForCourseScores(courseId).pipe(map((plagiarismCases) => plagiarismCases.map(toPlagiarismCaseForScores)));
    }

    /**
     * Get all plagiarism cases for the instructor of the course with the given id
     * @param courseId id of the course
     */
    public getCoursePlagiarismCasesForInstructor(courseId: number): Observable<PlagiarismCase[]> {
        return this.plagiarismCaseApi.getPlagiarismCasesForCourseForInstructor(courseId).pipe(map((plagiarismCases) => plagiarismCases.map(toPlagiarismCaseFromOverview)));
    }

    /**
     * Get all plagiarism cases for the instructor of the exam with the given id
     * @param courseId id of the course
     * @param examId id of the exam
     */
    public getExamPlagiarismCasesForInstructor(courseId: number, examId: number): Observable<PlagiarismCase[]> {
        return this.plagiarismCaseApi.getPlagiarismCasesForExamForInstructor(courseId, examId).pipe(map((plagiarismCases) => plagiarismCases.map(toPlagiarismCaseFromOverview)));
    }

    /**
     * Get the plagiarism case with the given id for the instructor
     * @param courseId id of the course
     * @param plagiarismCaseId id of the plagiarismCase
     */
    public getPlagiarismCaseDetailForInstructor(courseId: number, plagiarismCaseId: number): Observable<PlagiarismCase> {
        return this.plagiarismCaseApi.getPlagiarismCaseForInstructor(courseId, plagiarismCaseId).pipe(map(toPlagiarismCaseFromDetail));
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
    ): Observable<PlagiarismCaseVerdictResponse> {
        // Only the fields the caller set are sent; the server defaults a missing point deduction to 0.
        const request: PlagiarismVerdictRequest = { verdict: plagiarismVerdict.verdict };
        if (plagiarismVerdict.verdictMessage !== undefined) {
            request.verdictMessage = plagiarismVerdict.verdictMessage;
        }
        if (plagiarismVerdict.verdictPointDeduction !== undefined) {
            request.verdictPointDeduction = plagiarismVerdict.verdictPointDeduction;
        }
        return this.plagiarismCaseApi.savePlagiarismCaseVerdict(courseId, plagiarismCaseId, request).pipe(map(toPlagiarismCaseVerdictResponse));
    }

    /* Student */

    /**
     * Get the plagiarism case infos for the student for the given course and exercise id list for the exercises that the student is allowed to access.
     * @param courseId id of the course
     * @param exerciseIds ids of the exercises, sent as one repeated exerciseId query parameter each
     */
    public getPlagiarismCaseInfosForStudent(courseId: number, exerciseIds: number[]): Observable<{ [exerciseId: number]: PlagiarismCaseInfo }> {
        return this.plagiarismCaseApi.getPlagiarismCasesForExercisesForStudent(courseId, exerciseIds).pipe(map(toPlagiarismCaseInfoMap));
    }

    /**
     * Get the plagiarism case with the given id for the student
     * @param courseId id of the course
     * @param plagiarismCaseId id of the plagiarismCase
     */
    public getPlagiarismCaseDetailForStudent(courseId: number, plagiarismCaseId: number): Observable<PlagiarismCase> {
        return this.plagiarismCaseApi.getPlagiarismCaseForStudent(courseId, plagiarismCaseId).pipe(map(toPlagiarismCaseFromDetail));
    }

    /**
     * Get the plagiarism comparison with the given id. Emits undefined when the server sent the comparison without one of its two submissions.
     * @param courseId
     * @param plagiarismComparisonId
     */
    public getPlagiarismComparisonForSplitView(courseId: number, plagiarismComparisonId: number): Observable<PlagiarismComparison | undefined> {
        return this.plagiarismApi.getPlagiarismComparisonForSplitView(courseId, plagiarismComparisonId).pipe(map(toPlagiarismComparison));
    }

    /**
     * Update the status of the plagiarism comparison with given id
     * @param courseId
     * @param plagiarismComparisonId
     * @param { PlagiarismStatus } status
     */
    public updatePlagiarismComparisonStatus(courseId: number, plagiarismComparisonId: number, status: PlagiarismStatus): Observable<void> {
        return this.plagiarismApi.updatePlagiarismComparisonStatus(courseId, plagiarismComparisonId, { status });
    }

    /**
     * Clean up plagiarism results and comparisons
     * If deleteAll is set to true, all plagiarism results belonging to the exercise are deleted,
     * otherwise only plagiarism comparisons or with status DENIED or CONFIRMED are deleted and old results are deleted as well.
     *
     * @param exerciseId
     * @param plagiarismResultId
     * @param deleteAll sent as deleteAll=true or deleteAll=false
     */
    public cleanUpPlagiarism(exerciseId: number, plagiarismResultId: number, deleteAll = false): Observable<void> {
        return this.plagiarismApi.deletePlagiarismComparisons(exerciseId, plagiarismResultId, deleteAll);
    }

    public getNumberOfPlagiarismCasesForExercise(exercise: Exercise): Observable<number> {
        let courseId: number;
        if (exercise.exerciseGroup) {
            courseId = exercise.exerciseGroup.exam!.course!.id!;
        } else {
            courseId = exercise.course!.id!;
        }
        return this.plagiarismCaseApi.getNumberOfPlagiarismCasesForExercise(courseId, exercise.id!);
    }
}
