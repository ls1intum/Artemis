import { Injectable, inject } from '@angular/core';
import dayjs from 'dayjs/esm';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { omit as _omit } from 'lodash-es';

import { createRequestOption } from 'app/shared/util/request.util';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/solution-programming-exercise-participation.model';
import { PlagiarismOptions } from 'app/plagiarism/shared/entities/PlagiarismOptions';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { convertDateFromClient, convertDateFromServer } from 'app/shared/util/date.utils';
import { SortService } from 'app/shared/service/sort.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { PlagiarismResultDTO } from 'app/plagiarism/shared/entities/PlagiarismResultDTO';
import { ImportOptions } from 'app/programming/manage/programming-exercises';
import { CheckoutDirectoriesDto } from 'app/programming/shared/entities/checkout-directories-dto';
import { ProgrammingExerciseTheiaConfig } from 'app/programming/shared/entities/programming-exercise-theia.config';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';

export type EntityResponseType = HttpResponse<ProgrammingExercise>;
export type EntityArrayResponseType = HttpResponse<ProgrammingExercise[]>;

export type ProgrammingExerciseTestCaseStateDTO = {
    released: boolean;
    hasStudentResult: boolean;
    testCasesChanged: boolean;
    buildAndTestStudentSubmissionsAfterDueDate?: dayjs.Dayjs;
};

export type ProgrammingExerciseResetOptions = {
    deleteParticipationsSubmissionsAndResults: boolean;
    recreateBuildPlans: boolean;
};

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseService {
    private http = inject(HttpClient);
    private exerciseService = inject(ExerciseService);
    private sortService = inject(SortService);

    public resourceUrl = 'api/programming/programming-exercises';

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
     * Resets a programming exercise with the given exerciseId by performing a set of operations
     * as specified in the ProgrammingExerciseResetOptions. The available operations include:
     * 1. `deleteParticipationsSubmissionsAndResults`: Deleting all participations, submissions, and results (also deletes repositories and build plans).
     * 2. `recreateBuildPlans`: Deleting and recreating the BASE and SOLUTION build plans (for LocalCI / Aeolus, this will reset the customized build plans).
     *
     * @param exerciseId - of the programming exercise that should be reset.
     * @param options - Configuration options specifying which operations to perform during the exercise reset.
     * @returns An Observable that returns a string response.
     */
    reset(exerciseId: number, options: ProgrammingExerciseResetOptions): Observable<string> {
        return this.http.put(`${this.resourceUrl}/${exerciseId}/reset`, options, { responseType: 'text' });
    }

    /**
     * Check plagiarism with JPlag
     *
     * @param exerciseId
     * @param options
     */
    checkPlagiarism(exerciseId: number, options?: PlagiarismOptions): Observable<PlagiarismResultDTO> {
        return this.http
            .get<PlagiarismResultDTO>(`${this.resourceUrl}/${exerciseId}/check-plagiarism`, {
                observe: 'response',
                params: {
                    ...options?.toParams(),
                },
            })
            .pipe(map((response: HttpResponse<PlagiarismResultDTO>) => response.body!));
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
    getLatestPlagiarismResult(exerciseId: number): Observable<PlagiarismResultDTO> {
        return this.http
            .get<PlagiarismResultDTO>(`${this.resourceUrl}/${exerciseId}/plagiarism-result`, {
                observe: 'response',
            })
            .pipe(map((response: HttpResponse<PlagiarismResultDTO>) => response.body!));
    }

    /**
     * Imports a programming exercise by cloning the entity itself plus all basic build plans and repositories
     * (template, solution, test).
     *
     * @param adaptedSourceProgrammingExercise The exercise that should be imported, including adapted values for the
     *                                         new exercise. E.g. with another title than the original exercise. Old
     *                                         values that should get discarded (like the old ID) will be handled by the
     *                                         server.
     * @param importOptions see {@link ImportOptions}
     */
    importExercise(adaptedSourceProgrammingExercise: ProgrammingExercise, importOptions: ImportOptions): Observable<EntityResponseType> {
        const options = createRequestOption(importOptions);
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
     * @param withPlagiarismDetectionConfig true if plagiarism detection context should be fetched with the exercise
     * @param withAthenaConfig true if athena config should be fetched with the exercise
     */
    find(programmingExerciseId: number, withPlagiarismDetectionConfig: boolean = false, withAthenaConfig: boolean = false): Observable<EntityResponseType> {
        return this.http
            .get<ProgrammingExercise>(`${this.resourceUrl}/${programmingExerciseId}`, {
                observe: 'response',
                params: { withPlagiarismDetectionConfig: withPlagiarismDetectionConfig, withAthenaConfig: withAthenaConfig },
            })
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
     * @param withGradingCriteria also fetch the grading instructions for this exercise
     */
    findWithTemplateAndSolutionParticipation(programmingExerciseId: number, withSubmissionResults = false, withGradingCriteria = false): Observable<EntityResponseType> {
        let params = new HttpParams();
        params = params.set('withSubmissionResults', withSubmissionResults.toString()).set('withGradingCriteria', withGradingCriteria.toString());
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
     * Finds the programming exercise for the given exerciseId with the template and solution participation and their latest result each.
     * @param programmingExerciseId of the programming exercise to retrieve
     */
    findWithTemplateAndSolutionParticipationAndLatestResults(programmingExerciseId: number): Observable<EntityResponseType> {
        return this.findWithTemplateAndSolutionParticipation(programmingExerciseId, true).pipe(
            map((response) => {
                if (response.body) {
                    this.setLatestResultForTemplateAndSolution(response.body);
                }
                return response;
            }),
        );
    }

    /**
     * Finds the programming exercise for the given exerciseId with its auxiliary repositories
     * @param programmingExerciseId of the programming exercise to retrieve
     */
    findWithAuxiliaryRepository(programmingExerciseId: number): Observable<EntityResponseType> {
        return this.http.get<ProgrammingExercise>(`${this.resourceUrl}/${programmingExerciseId}/with-auxiliary-repository`, {
            observe: 'response',
        });
    }

    private setLatestResultForTemplateAndSolution(programmingExercise: ProgrammingExercise) {
        if (programmingExercise.templateParticipation) {
            const latestTemplateResult = this.getLatestResult(programmingExercise.templateParticipation);
            if (latestTemplateResult) {
                programmingExercise.templateParticipation.submissions!.last()!.results = [latestTemplateResult];
            }
            // This is needed to access the exercise in the result details
            programmingExercise.templateParticipation.programmingExercise = programmingExercise;
        }

        if (programmingExercise.solutionParticipation) {
            const latestSolutionResult = this.getLatestResult(programmingExercise.solutionParticipation);
            if (latestSolutionResult) {
                programmingExercise.solutionParticipation.submissions!.last()!.results = [latestSolutionResult];
            }
            // This is needed to access the exercise in the result details
            programmingExercise.solutionParticipation.programmingExercise = programmingExercise;
        }
    }

    /**
     * Finds the result that has the latest submission date.
     *
     * @param participation Some participation.
     */
    getLatestResult(participation: Participation): Result | undefined {
        const submissions = participation.submissions;
        if (!submissions || submissions.length === 0) {
            return;
        }

        // important: sort to get the latest submission (the order of the server can be random)
        this.sortService.sortByProperty(submissions, 'submissionDate', true);
        const results = submissions.sort().last()?.results;
        if (results && results.length > 0) {
            return results.last();
        }
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
    delete(programmingExerciseId: number, deleteStudentReposBuildPlans: boolean, deleteBaseReposBuildPlans: boolean): Observable<HttpResponse<any>> {
        let params = new HttpParams();
        if (deleteBaseReposBuildPlans != undefined && deleteStudentReposBuildPlans != undefined) {
            params = params.set('deleteStudentReposBuildPlans', deleteStudentReposBuildPlans.toString());
            params = params.set('deleteBaseReposBuildPlans', deleteBaseReposBuildPlans.toString());
        }
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
     * Exports the solution, template or test repository for a given exercise.
     * @param exerciseId
     * @param repositoryType
     * @param auxiliaryRepositoryId
     */
    exportInstructorRepository(exerciseId: number, repositoryType: RepositoryType, auxiliaryRepositoryId: number | undefined): Observable<HttpResponse<Blob>> {
        if (repositoryType === RepositoryType.AUXILIARY && auxiliaryRepositoryId !== undefined) {
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
     * Exports the repository belonging to a specific student participation of a programming exercise.
     * @param exerciseId The ID of the programming exercise.
     * @param participationId The ID of the (student) participation
     */
    exportStudentRepository(exerciseId: number, participationId: number): Observable<HttpResponse<Blob>> {
        return this.http.get(`${this.resourceUrl}/${exerciseId}/export-student-repository/${participationId}`, {
            observe: 'response',
            responseType: 'blob',
        });
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
     * @param includeTests flag that indicates whether the tests should also be exported
     */
    exportStudentRequestedRepository(exerciseId: number, includeTests: boolean): Observable<HttpResponse<Blob>> {
        let params = new HttpParams();
        params = params.set('includeTests', includeTests.toString());
        return this.http.get(`${this.resourceUrl}/${exerciseId}/export-student-requested-repository`, {
            params,
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
        this.exerciseService.sendExerciseTitleToTitleService(exerciseRes?.body ?? undefined);
        return exerciseRes;
    }

    /**
     * Gets all files from the last solution participation repository
     */
    getSolutionRepositoryTestFilesWithContent(exerciseId: number): Observable<Map<string, string> | undefined> {
        return this.http.get(`${this.resourceUrl}/${exerciseId}/solution-files-content?omitBinaries=true`).pipe(
            map((res: HttpResponse<any>) => {
                // this mapping is required because otherwise the HttpResponse object would be parsed
                // to an arbitrary object (and not a map)
                return res && new Map(Object.entries(res));
            }),
        );
    }

    /**
     * Gets all files from the last commit in the template participation repository
     */
    getTemplateRepositoryTestFilesWithContent(exerciseId: number): Observable<Map<string, string> | undefined> {
        return this.http.get(`${this.resourceUrl}/${exerciseId}/template-files-content?omitBinaries=true`).pipe(
            map((res: HttpResponse<any>) => {
                // this mapping is required because otherwise the HttpResponse object would be parsed
                // to an arbitrary object (and not a map)
                return res && new Map(Object.entries(res));
            }),
        );
    }

    getTheiaConfig(exerciseId: number): Observable<ProgrammingExerciseTheiaConfig> {
        return this.http.get<ProgrammingExerciseTheiaConfig>(`${this.resourceUrl}/${exerciseId}/theia-config`);
    }

    /** Imports a programming exercise from a given zip file **/
    importFromFile(exercise: ProgrammingExercise, courseId: number): Observable<EntityResponseType> {
        let copy = this.convertDataFromClient(exercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        const formData = new FormData();
        formData.append('file', exercise.zipFileForImport!);
        const exerciseBlob = new Blob([JSON.stringify(copy)], { type: 'application/json' });
        formData.append('programmingExercise', exerciseBlob);
        const url = `api/programming/courses/${courseId}/programming-exercises/import-from-file`;
        return this.http
            .post<ProgrammingExercise>(url, formData, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processProgrammingExerciseEntityResponse(res)));
    }

    getCheckoutDirectoriesForProgrammingLanguage(programmingLanguage: ProgrammingLanguage, checkoutSolution: boolean): Observable<CheckoutDirectoriesDto> {
        return this.http.get<CheckoutDirectoriesDto>(`${this.resourceUrl}/repository-checkout-directories`, {
            params: {
                programmingLanguage,
                checkoutSolution: checkoutSolution,
            },
        });
    }
}
