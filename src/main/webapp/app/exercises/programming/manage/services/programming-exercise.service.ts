import { Injectable } from '@angular/core';
import * as moment from 'moment';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { map } from 'rxjs/operators';
import { omit as _omit } from 'lodash';
import { SERVER_API_URL } from 'app/app.constants';

import { createRequestOption } from 'app/shared/util/request-util';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { Moment } from 'moment';
import { TextPlagiarismResult } from 'app/exercises/shared/plagiarism/types/text/TextPlagiarismResult';

export type EntityResponseType = HttpResponse<ProgrammingExercise>;
export type EntityArrayResponseType = HttpResponse<ProgrammingExercise[]>;

export type ProgrammingExerciseTestCaseStateDTO = {
    released: boolean;
    hasStudentResult: boolean;
    testCasesChanged: boolean;
    buildAndTestStudentSubmissionsAfterDueDate?: Moment;
};

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseService {
    public resourceUrl = SERVER_API_URL + 'api/programming-exercises';

    constructor(private http: HttpClient, private exerciseService: ExerciseService) {}

    /**
     * Sets a new programming exercise up
     * @param programmingExercise which should be setup
     */
    automaticSetup(programmingExercise: ProgrammingExercise): Observable<EntityResponseType> {
        const copy = this.convertDataFromClient(programmingExercise);
        return this.http
            .post<ProgrammingExercise>(this.resourceUrl + '/setup', copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * Generates the structure oracle
     * @param exerciseId of the programming exercise for which the structure oracle should be created
     */
    generateStructureOracle(exerciseId: number): Observable<string> {
        return this.http.put<string>(this.resourceUrl + '/' + exerciseId + '/generate-tests', { responseType: 'text' });
    }

    /**
     * Check plagiarism with JPlag
     *
     * @param exerciseId
     */
    checkPlagiarism(exerciseId: number): Observable<TextPlagiarismResult> {
        return this.http
            .get<TextPlagiarismResult>(`${this.resourceUrl}/${exerciseId}/check-plagiarism`, {
                observe: 'response',
            })
            .pipe(map((response: HttpResponse<TextPlagiarismResult>) => response.body!));
    }

    /**
     * Check for plagiarism
     * @param exerciseId of the programming exercise
     */
    checkPlagiarismJPlagReport(exerciseId: number): Observable<HttpResponse<Blob>> {
        return this.http.get(`${this.resourceUrl}/${exerciseId}/check-plagiarism-jplag-report`, {
            observe: 'response',
            responseType: 'blob',
        });
    }

    /**
     * Combines all commits of the template repository to one
     * @param exerciseId of the particular programming exercise
     */
    combineTemplateRepositoryCommits(exerciseId: number) {
        return this.http.put(this.resourceUrl + '/' + exerciseId + '/combine-template-commits', { responseType: 'text' });
    }

    /**
     * Imports a programming exercise by cloning the entity itself plus all bas build plans and repositories
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
        return this.http
            .post<ProgrammingExercise>(`${this.resourceUrl}/import/${adaptedSourceProgrammingExercise.id}`, adaptedSourceProgrammingExercise, {
                params: options,
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)));
    }

    /**
     * Updates an existing programming exercise
     * @param programmingExercise which should be updated
     * @param req optional request options
     */
    update(programmingExercise: ProgrammingExercise, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        const copy = this.convertDataFromClient(programmingExercise);
        return this.http
            .put<ProgrammingExercise>(this.resourceUrl, copy, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
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
            .patch<ProgrammingExercise>(`${this.resourceUrl}/${programmingExerciseId}/problem-statement`, problemStatement, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * Finds the programming exercise for the given exerciseId
     * @param programmingExerciseId of the programming exercise to retrieve
     */
    find(programmingExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<ProgrammingExercise>(`${this.resourceUrl}/${programmingExerciseId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * Finds the programming exercise for the given exerciseId with the corresponding participation's
     * @param programmingExerciseId of the programming exercise to retrieve
     */
    findWithTemplateAndSolutionParticipation(programmingExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<ProgrammingExercise>(`${this.resourceUrl}/${programmingExerciseId}/with-participations`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * Returns a entity with true in the body if there is a programming exercise with the given id, it is released (release date < now) and there is at least one student result.
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
            .pipe(map((res: EntityArrayResponseType) => this.exerciseService.convertDateArrayFromServer(res)));
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
            ...this.exerciseService.convertDateFromClient(exercise),
            buildAndTestStudentSubmissionsAfterDueDate:
                exercise.buildAndTestStudentSubmissionsAfterDueDate && moment(exercise.buildAndTestStudentSubmissionsAfterDueDate).isValid()
                    ? moment(exercise.buildAndTestStudentSubmissionsAfterDueDate).toJSON()
                    : undefined,
        };
        // Remove exercise from template & solution participation to avoid circular dependency issues.
        // Also remove the results, as they can have circular structures as well and don't have to be saved here.
        if (copy.templateParticipation) {
            copy.templateParticipation = _omit(copy.templateParticipation, ['exercise', 'results']) as TemplateProgrammingExerciseParticipation;
        }
        if (copy.solutionParticipation) {
            copy.solutionParticipation = _omit(copy.solutionParticipation, ['exercise', 'results']) as SolutionProgrammingExerciseParticipation;
        }

        return copy;
    }

    /**
     * Convert all date fields of the programming exercise to momentJs date objects.
     * Note: This conversion could produce an invalid date if the date is malformatted.
     *
     * @param entity ProgrammingExercise
     */
    convertDateFromServer(entity: EntityResponseType) {
        const res = this.exerciseService.convertDateFromServer(entity);
        if (!res.body) {
            return res;
        }
        res.body.buildAndTestStudentSubmissionsAfterDueDate = res.body.buildAndTestStudentSubmissionsAfterDueDate
            ? moment(res.body.buildAndTestStudentSubmissionsAfterDueDate)
            : undefined;
        return res;
    }

    /**
     * Unlock all the student repositories of the given exercise so that student can perform commits
     * @param exerciseId of the particular programming exercise
     */
    unlockAllRepositories(exerciseId: number): Observable<HttpResponse<{}>> {
        return this.http.put<any>(`${this.resourceUrl}/programming-exercises/${exerciseId}/unlock-all-repositories`, {}, { observe: 'response' });
    }

    /**
     * Lock all the student repositories of the given exercise so that student can perform commits
     * @param exerciseId of the particular programming exercise
     */
    lockAllRepositories(exerciseId: number): Observable<HttpResponse<{}>> {
        return this.http.put<any>(`${this.resourceUrl}/programming-exercises/${exerciseId}/lock-all-repositories`, {}, { observe: 'response' });
    }
}
