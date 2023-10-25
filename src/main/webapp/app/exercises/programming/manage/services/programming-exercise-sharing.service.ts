import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { omit as _omit } from 'lodash';
import { map } from 'rxjs/operators';
import dayjs from 'dayjs/esm';

import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';

import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { SharingInfo, ShoppingBasket } from 'app/sharing/sharing.model';
import { Course } from 'app/entities/course.model';

export type EntityResponseType = HttpResponse<ProgrammingExercise>;
export type EntityArrayResponseType = HttpResponse<ProgrammingExercise[]>;

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseSharingService {
    baseSharingConfigUrl = 'api/sharing/config';
    resourceUrl = 'api/sharing/import';
    resourceUrlBasket = 'api/sharing/import/basket/';
    resourceUrlExport = 'api/sharing/export';

    constructor(private http: HttpClient) {}

    getSharedExercises(sharingInfo: SharingInfo): Observable<ShoppingBasket> {
        return this.http
            .get<ShoppingBasket>(this.resourceUrl + '/basket', {
                params: { basketToken: sharingInfo.basketToken, apiBaseUrl: sharingInfo.apiBaseURL },
                observe: 'response',
            })
            .pipe(map((response: HttpResponse<ShoppingBasket>) => response.body!));
    }

    loadProblemStatementForExercises(sharingInfo: SharingInfo): Observable<string> {
        const headers = new HttpHeaders();
        return this.http.post<string>(this.resourceUrlBasket + 'problemStatement', sharingInfo, { headers, responseType: 'text' as 'json' });
    }

    loadDetailsForExercises(sharingInfo: SharingInfo): Observable<ProgrammingExercise> {
        return this.http.post<ProgrammingExercise>(this.resourceUrlBasket + 'exerciseDetails', sharingInfo);
    }

    /**
     * Sets a new programming exercise up
     * @param programmingExercise which should be setup
     * @param course in which the exercise should be imported
     * @param sharingInfo sharing related data needed for the import
     */
    setUpFromSharingImport(programmingExercise: ProgrammingExercise, course: Course, sharingInfo: SharingInfo): Observable<EntityResponseType> {
        let copy = this.convertDataFromClient(programmingExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        return this.http
            .post<ProgrammingExercise>('api/sharing/setup-import', { exercise: copy, course, sharingInfo }, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * Converts the data from the client
     * if template & solution participation exist removes the exercise and results from them
     * @param exercise for which the data should be converted
     */
    convertDataFromClient(exercise: ProgrammingExercise) {
        const copy = {
            ...ExerciseService.convertExerciseDatesFromClient(exercise),
            buildAndTestStudentSubmissionsAfterDueDate:
                exercise.buildAndTestStudentSubmissionsAfterDueDate && dayjs(exercise.buildAndTestStudentSubmissionsAfterDueDate).isValid()
                    ? dayjs(exercise.buildAndTestStudentSubmissionsAfterDueDate).toJSON()
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
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);

        return copy as ProgrammingExercise;
    }

    /**
     * Convert all date fields of the programming exercise to momentJs date objects.
     * Note: This conversion could produce an invalid date if the date is malformatted.
     *
     * @param entity ProgrammingExercise
     */
    convertDateFromServer(entity: EntityResponseType) {
        const res = ExerciseService.convertExerciseResponseDatesFromServer(entity);
        if (!res.body) {
            return res;
        }
        res.body.buildAndTestStudentSubmissionsAfterDueDate = res.body.buildAndTestStudentSubmissionsAfterDueDate
            ? dayjs(res.body.buildAndTestStudentSubmissionsAfterDueDate)
            : undefined;
        return res;
    }

    /**
     * Used to initiate export to Sharing.
     * This returns a URL to Sharing with a callback as parameter to the exposed exercise in Artemis
     * @param programmingExerciseId id of the exercise to export
     * @param callBackUrl used to redirect back after export has been completed
     */
    exportProgrammingExerciseToSharing(programmingExerciseId: number, callBackUrl: string) {
        return this.http.post(this.resourceUrlExport + `/${programmingExerciseId}`, callBackUrl, {
            observe: 'response',
        });
    }

    /**
     * Check if Sharing has been enabled
     * (if getConfig call has arrived from Sharing)
     */
    isSharingEnabled() {
        return this.http.get<boolean>(this.baseSharingConfigUrl + '/is-enabled', {
            observe: 'response',
        });
    }
}
