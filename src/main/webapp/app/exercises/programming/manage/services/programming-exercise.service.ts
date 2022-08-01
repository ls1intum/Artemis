import { Injectable } from '@angular/core';
import dayjs from 'dayjs/esm';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { omit as _omit } from 'lodash-es';

import { createRequestOption } from 'app/shared/util/request.util';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { TextPlagiarismResult } from 'app/exercises/shared/plagiarism/types/text/TextPlagiarismResult';
import { PlagiarismOptions } from 'app/exercises/shared/plagiarism/types/PlagiarismOptions';
import { Submission } from 'app/entities/submission.model';
import { ProgrammingExerciseFullGitDiffReport } from 'app/entities/hestia/programming-exercise-full-git-diff-report.model';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { CoverageReport } from 'app/entities/hestia/coverage-report.model';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { ProgrammingExerciseServerSideTask } from 'app/entities/hestia/programming-exercise-task.model';
import { convertDateFromClient, convertDateFromServer } from 'app/utils/date.utils';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';

export type EntityResponseType = HttpResponse<ProgrammingExercise>;
export type EntityArrayResponseType = HttpResponse<ProgrammingExercise[]>;

export type ProgrammingExerciseTestCaseStateDTO = {
    released: boolean;
    hasStudentResult: boolean;
    testCasesChanged: boolean;
    buildAndTestStudentSubmissionsAfterDueDate?: dayjs.Dayjs;
};

// TODO: we should use a proper enum here
export type ProgrammingExerciseInstructorRepositoryType = 'TEMPLATE' | 'SOLUTION' | 'TESTS' | 'AUXILIARY';

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseService {
    public resourceUrl = SERVER_API_URL + 'api/programming-exercises';

    constructor(private http: HttpClient, private exerciseService: ExerciseService) {}

    /**
     * Sets a new programming exercise up
     * @param programmingExercise which should be setup
     */
    automaticSetup(programmingExercise: ProgrammingExercise): Observable<EntityResponseType> {
        let copy = this.convertDataFromClient(programmingExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .post<ProgrammingExercise>(this.resourceUrl + '/setup', copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processProgrammingExerciseEntityResponse(res)));
    }

    /**
     * Generates the structure oracle
     * @param exerciseId of the programming exercise for which the structure oracle should be created
     */
    generateStructureOracle(exerciseId: number): Observable<string> {
        return this.http.put<string>(`${this.resourceUrl}/${exerciseId}/generate-tests`, { responseType: 'text' });
    }

    /**
     * Recreates the BASE and SOLUTION build plan for this exercise
     * @param exerciseId of the programming exercise for which the build plans should be recreated
     */
    recreateBuildPlans(exerciseId: number): Observable<string> {
        return this.http.put<string>(`${this.resourceUrl}/${exerciseId}/recreate-build-plans`, { responseType: 'text' });
    }

    /**
     * Check plagiarism with JPlag
     *
     * @param exerciseId
     * @param options
     */
    checkPlagiarism(exerciseId: number, options?: PlagiarismOptions): Observable<TextPlagiarismResult> {
        return this.http
            .get<TextPlagiarismResult>(`${this.resourceUrl}/${exerciseId}/check-plagiarism`, {
                observe: 'response',
                params: {
                    ...options?.toParams(),
                },
            })
            .pipe(map((response: HttpResponse<TextPlagiarismResult>) => response.body!));
    }

    /**
     * Check for plagiarism
     * @param exerciseId of the programming exercise
     * @param options
     */
    checkPlagiarismJPlagReport(exerciseId: number, options?: PlagiarismOptions): Observable<HttpResponse<Blob>> {
        return this.http.get(`${this.resourceUrl}/${exerciseId}/check-plagiarism-jplag-report`, {
            observe: 'response',
            responseType: 'blob',
            params: {
                ...options?.toParams(),
            },
        });
    }

    /**
     * Get the latest plagiarism result for the exercise with the given ID.
     *
     * @param exerciseId
     */
    getLatestPlagiarismResult(exerciseId: number): Observable<TextPlagiarismResult> {
        return this.http
            .get<TextPlagiarismResult>(`${this.resourceUrl}/${exerciseId}/plagiarism-result`, {
                observe: 'response',
            })
            .pipe(map((response: HttpResponse<TextPlagiarismResult>) => response.body!));
    }

    /**
     * Combines all commits of the template repository to one
     * @param exerciseId of the particular programming exercise
     */
    combineTemplateRepositoryCommits(exerciseId: number) {
        return this.http.put(`${this.resourceUrl}/${exerciseId}/combine-template-commits`, { responseType: 'text' });
    }

    /**
     * Imports a programming exercise by cloning the entity itself plus all basic build plans and repositories
     * (template, solution, test).
     *
     * @param adaptedSourceProgrammingExercise The exercise that should be imported, including adapted values for the
     *                                         new exercise. E.g. with another title than the original exercise. Old
     *                                         values that should get discarded (like the old ID) will be handled by the
     *                                         server.
     * @param recreateBuildPlans Option determining whether the build plans should be recreated or copied from the imported exercise
     * @param updateTemplate Option determining whether the template files in the repositories should be updated
     */
    importExercise(adaptedSourceProgrammingExercise: ProgrammingExercise, recreateBuildPlans: boolean, updateTemplate: boolean): Observable<EntityResponseType> {
        const options = createRequestOption({ recreateBuildPlans, updateTemplate });
        const exercise = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(adaptedSourceProgrammingExercise);
        exercise.categories = ExerciseService.stringifyExerciseCategories(exercise);
        return this.http
            .post<ProgrammingExercise>(`${this.resourceUrl}/import/${adaptedSourceProgrammingExercise.id}`, exercise, {
                params: options,
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.processProgrammingExerciseEntityResponse(res)));
    }

    /**
     * Updates an existing programming exercise
     * @param programmingExercise which should be updated
     * @param req optional request options
     */
    update(programmingExercise: ProgrammingExercise, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        let copy = this.convertDataFromClient(programmingExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .put<ProgrammingExercise>(this.resourceUrl, copy, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processProgrammingExerciseEntityResponse(res)));
    }

    /**
     * Updates the timeline of a programming exercise
     * @param programmingExercise to update
     * @param req optional request options
     */
    updateTimeline(programmingExercise: ProgrammingExercise, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        const copy = this.convertDataFromClient(programmingExercise);
        return this.http
            .put<ProgrammingExercise>(`${this.resourceUrl}/timeline`, copy, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processProgrammingExerciseEntityResponse(res)));
    }

    /**
     * Updates the problem statement
     * @param programmingExerciseId of the programming exercise for which to change the problem statement
     * @param problemStatement the new problem statement
     * @param req optional request options
     */
    updateProblemStatement(programmingExerciseId: number, problemStatement: string, req?: any) {
        const options = createRequestOption(req);
        return this.http
            .patch<ProgrammingExercise>(`${this.resourceUrl}/${programmingExerciseId}/problem-statement`, problemStatement, {
                params: options,
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.processProgrammingExerciseEntityResponse(res)));
    }

    /**
     * Finds the programming exercise for the given exerciseId
     * @param programmingExerciseId of the programming exercise to retrieve
     */
    find(programmingExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<ProgrammingExercise>(`${this.resourceUrl}/${programmingExerciseId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processProgrammingExerciseEntityResponse(res)));
    }

    /**
     * Finds the programming exercise for the given exerciseId with the corresponding participation's with results
     * @param programmingExerciseId of the programming exercise to retrieve
     */
    findWithTemplateAndSolutionParticipationAndResults(programmingExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<ProgrammingExercise>(`${this.resourceUrl}/${programmingExerciseId}/with-participations`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processProgrammingExerciseEntityResponse(res)));
    }

    /**
     * Finds the programming exercise for the given exerciseId with the template and solution participation
     * @param programmingExerciseId of the programming exercise to retrieve
     * @param withSubmissionResults get results attached to submissions
     */
    findWithTemplateAndSolutionParticipation(programmingExerciseId: number, withSubmissionResults = false): Observable<EntityResponseType> {
        let params = new HttpParams();
        params = params.set('withSubmissionResults', withSubmissionResults.toString());
        return this.http
            .get<ProgrammingExercise>(`${this.resourceUrl}/${programmingExerciseId}/with-template-and-solution-participation`, {
                params,
                observe: 'response',
            })
            .pipe(
                map((res: EntityResponseType) => {
                    if (res.body && withSubmissionResults) {
                        // We need to reconnect the submissions with the results. They got removed because of the circular dependency
                        const templateSubmissions = res.body.templateParticipation?.submissions;
                        this.reconnectSubmissionAndResult(templateSubmissions);
                        const solutionSubmissions = res.body.solutionParticipation?.submissions;
                        this.reconnectSubmissionAndResult(solutionSubmissions);

                        this.processProgrammingExerciseEntityResponse(res);
                    }
                    return res;
                }),
            );
    }

    /**
     * Reconnecting the missing submission of a submission's result
     *
     * @param submissions where the results have no reference to its submission
     */
    private reconnectSubmissionAndResult(submissions: Submission[] | undefined) {
        if (submissions) {
            submissions.forEach((submission) => {
                if (submission.results) {
                    submission.results.forEach((result) => {
                        result.submission = submission;
                    });
                }
            });
        }
    }

    /**
     * Returns an entity with true in the body if there is a programming exercise with the given id, it is released (release date < now) and there is at least one student result.
     *
     * @param exerciseId ProgrammingExercise id
     */
    getProgrammingExerciseTestCaseState(exerciseId: number): Observable<HttpResponse<ProgrammingExerciseTestCaseStateDTO>> {
        return this.http.get<ProgrammingExerciseTestCaseStateDTO>(`${this.resourceUrl}/${exerciseId}/test-case-state`, { observe: 'response' });
    }

    /**
     * Receives all programming exercises for the particular query
     * @param req optional request options
     */
    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<ProgrammingExercise[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.exerciseService.processExerciseEntityArrayResponse(res)));
    }

    /**
     * Deletes the programming exercise with the corresponding programming exercise Id
     * @param programmingExerciseId of the programming exercise to delete
     * @param deleteStudentReposBuildPlans indicates if the StudentReposBuildPlans should be also deleted or not
     * @param deleteBaseReposBuildPlans indicates if the BaseReposBuildPlans should be also deleted or not
     */
    delete(programmingExerciseId: number, deleteStudentReposBuildPlans: boolean, deleteBaseReposBuildPlans: boolean): Observable<HttpResponse<{}>> {
        let params = new HttpParams();
        params = params.set('deleteStudentReposBuildPlans', deleteStudentReposBuildPlans.toString());
        params = params.set('deleteBaseReposBuildPlans', deleteBaseReposBuildPlans.toString());
        return this.http.delete(`${this.resourceUrl}/${programmingExerciseId}`, { params, observe: 'response' });
    }

    /**
     * Converts the data from the client
     * if template & solution participation exist removes the exercise and results from them
     * @param exercise for which the data should be converted
     */
    convertDataFromClient(exercise: ProgrammingExercise) {
        const copy = {
            ...ExerciseService.convertExerciseDatesFromClient(exercise),
            buildAndTestStudentSubmissionsAfterDueDate: convertDateFromClient(exercise.buildAndTestStudentSubmissionsAfterDueDate),
        };
        // Remove exercise from template & solution participation to avoid circular dependency issues.
        // Also remove the results, as they can have circular structures as well and don't have to be saved here.
        if (copy.templateParticipation) {
            copy.templateParticipation = _omit(copy.templateParticipation, ['exercise', 'results']) as TemplateProgrammingExerciseParticipation;
        }
        if (copy.solutionParticipation) {
            copy.solutionParticipation = _omit(copy.solutionParticipation, ['exercise', 'results']) as SolutionProgrammingExerciseParticipation;
        }

        return copy as ProgrammingExercise;
    }

    /**
     * Convert all date fields of the programming exercise to dayjs date objects.
     * Note: This conversion could produce an invalid date if the date is malformatted.
     *
     * @param entity ProgrammingExercise
     */
    static convertProgrammingExerciseResponseDatesFromServer(entity: EntityResponseType) {
        const res = ExerciseService.convertExerciseResponseDatesFromServer(entity);
        if (!res.body) {
            return res;
        }
        res.body.buildAndTestStudentSubmissionsAfterDueDate = convertDateFromServer(res.body.buildAndTestStudentSubmissionsAfterDueDate);
        return res;
    }

    /**
     * Unlock all the student repositories of the given exercise so that student can perform commits
     * @param exerciseId of the particular programming exercise
     */
    unlockAllRepositories(exerciseId: number): Observable<HttpResponse<{}>> {
        return this.http.put<any>(`${this.resourceUrl}/${exerciseId}/unlock-all-repositories`, {}, { observe: 'response' });
    }

    /**
     * Lock all the student repositories of the given exercise so that student can perform commits
     * @param exerciseId of the particular programming exercise
     */
    lockAllRepositories(exerciseId: number): Observable<HttpResponse<{}>> {
        return this.http.put<any>(`${this.resourceUrl}/${exerciseId}/lock-all-repositories`, {}, { observe: 'response' });
    }

    /**
     * Exports the solution, template or test repository for a given exercise.
     * @param exerciseId
     * @param repositoryType
     * @param auxiliaryRepositoryId
     */
    exportInstructorRepository(exerciseId: number, repositoryType: ProgrammingExerciseInstructorRepositoryType, auxiliaryRepositoryId: number): Observable<HttpResponse<Blob>> {
        if (repositoryType === 'AUXILIARY') {
            return this.http.get(`${this.resourceUrl}/${exerciseId}/export-instructor-auxiliary-repository/${auxiliaryRepositoryId}`, {
                observe: 'response',
                responseType: 'blob',
            });
        } else {
            return this.http.get(`${this.resourceUrl}/${exerciseId}/export-instructor-repository/${repositoryType}`, {
                observe: 'response',
                responseType: 'blob',
            });
        }
    }

    /**
     * Exports all instructor repositories (solution, template, test), the problem statement and the exercise details.
     * @param exerciseId
     */
    exportInstructorExercise(exerciseId: number): Observable<HttpResponse<Blob>> {
        return this.http.get(`${this.resourceUrl}/${exerciseId}/export-instructor-exercise`, {
            observe: 'response',
            responseType: 'blob',
        });
    }

    /**
     * Exports the example solution repository for a given exercise, suitable for distributing to students.
     * @param exerciseId
     */
    exportSolutionRepository(exerciseId: number): Observable<HttpResponse<Blob>> {
        return this.http.get(`${this.resourceUrl}/${exerciseId}/export-solution-repository`, {
            observe: 'response',
            responseType: 'blob',
        });
    }

    /**
     * Re-evaluates and updates an existing programming exercise.
     *
     * @param programmingExercise that should be updated of type {ProgrammingExercise}
     * @param req optional request options
     */
    reevaluateAndUpdate(programmingExercise: ProgrammingExercise, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        let copy = this.convertDataFromClient(programmingExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .put<ProgrammingExercise>(`${this.resourceUrl}/${programmingExercise.id}/re-evaluate`, copy, {
                params: options,
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * This method bundles recurring conversion steps for ProgrammingExercise EntityResponses.
     *
     * NOTE: This method is used instead of {@link exerciseService.processExerciseEntityResponse} due to the
     *       different handling of the date conversion
     * @param exerciseRes
     */
    private processProgrammingExerciseEntityResponse(exerciseRes: EntityResponseType): EntityResponseType {
        ProgrammingExerciseService.convertProgrammingExerciseResponseDatesFromServer(exerciseRes);
        ExerciseService.convertExerciseCategoriesFromServer(exerciseRes);
        this.exerciseService.setAccessRightsExerciseEntityResponseType(exerciseRes);
        this.exerciseService.sendExerciseTitleToTitleService(exerciseRes?.body);
        return exerciseRes;
    }

    /**
     * Get tasks and tests cases extracted from the problem statement for a programming exercise.
     * This method and all helper methods are only for testing reason and will be removed later on.
     * @param exerciseId the exercise id
     */
    getTasksAndTestsExtractedFromProblemStatement(exerciseId: number): Observable<ProgrammingExerciseServerSideTask[]> {
        return this.http
            .get(`${this.resourceUrl}/${exerciseId}/tasks`, { observe: 'response' })
            .pipe(map((res: HttpResponse<ProgrammingExerciseServerSideTask[]>) => res.body ?? []));
    }

    /**
     * Gets the git-diff report of a programming exercise
     *
     * @param exerciseId The id of a programming exercise
     */
    getDiffReport(exerciseId: number): Observable<ProgrammingExerciseGitDiffReport | undefined> {
        return this.http
            .get<ProgrammingExerciseGitDiffReport>(`${this.resourceUrl}/${exerciseId}/diff-report`, { observe: 'response' })
            .pipe(map((res: HttpResponse<ProgrammingExerciseGitDiffReport>) => res.body ?? undefined));
    }

    /**
     * Gets the full git-diff report of a programming exercise containing the previousCode and current code blocks
     *
     * @param exerciseId The id of a programming exercise
     */
    getFullDiffReport(exerciseId: number): Observable<ProgrammingExerciseFullGitDiffReport> {
        return this.http.get<ProgrammingExerciseFullGitDiffReport>(`${this.resourceUrl}/${exerciseId}/full-diff-report`);
    }

    /**
     * Gets the testwise coverage report of a programming exercise for the latest solution submission with all descending reports
     * @param exerciseId The id of a programming exercise
     */
    getLatestFullTestwiseCoverageReport(exerciseId: number): Observable<CoverageReport> {
        return this.http.get<CoverageReport>(`${this.resourceUrl}/${exerciseId}/full-testwise-coverage-report`);
    }

    /**
     * Gets the testwise coverage report of a programming exercise for the latest solution submission without the actual reports
     * @param exerciseId The id of a programming exercise
     */
    getLatestTestwiseCoverageReport(exerciseId: number): Observable<CoverageReport> {
        return this.http.get<CoverageReport>(`${this.resourceUrl}/${exerciseId}/full-testwise-coverage-report`);
    }

    /**
     * Gets all files from the last solution participation repository
     */
    getSolutionRepositoryTestFilesWithContent(exerciseId: number): Observable<Map<string, string> | undefined> {
        return this.http.get(`${this.resourceUrl}/${exerciseId}/solution-files-content`).pipe(
            map((res: HttpResponse<any>) => {
                // this mapping is required because otherwise the HttpResponse object would be parsed
                // to an arbitrary object (and not a map)
                return res && new Map(Object.entries(res));
            }),
        );
    }

    getSolutionFileNames(exerciseId: number): Observable<string[]> {
        return this.http.get<string[]>(`${this.resourceUrl}/${exerciseId}/file-names`);
    }

    getCodeHintsForExercise(exerciseId: number): Observable<ExerciseHint[]> {
        return this.http.get<ExerciseHint[]>(`${this.resourceUrl}/${exerciseId}/exercise-hints`);
    }

    createStructuralSolutionEntries(exerciseId: number): Observable<ProgrammingExerciseSolutionEntry[]> {
        return this.http.post<ProgrammingExerciseSolutionEntry[]>(`${this.resourceUrl}/${exerciseId}/structural-solution-entries`, null);
    }

    createBehavioralSolutionEntries(exerciseId: number): Observable<ProgrammingExerciseSolutionEntry[]> {
        return this.http.post<ProgrammingExerciseSolutionEntry[]>(`${this.resourceUrl}/${exerciseId}/behavioral-solution-entries`, null);
    }

    getAllTestCases(exerciseId: number): Observable<ProgrammingExerciseTestCase[]> {
        return this.http.get<ProgrammingExerciseTestCase[]>(`api/programming-exercises/${exerciseId}/test-cases`);
    }
}
