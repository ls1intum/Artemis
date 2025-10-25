import { HttpClient, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { MODULE_FEATURE_SHARING } from 'app/app.constants';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { SolutionProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/solution-programming-exercise-participation.model';

import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';

import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { SharingInfo, ShoppingBasket } from 'app/sharing/sharing.model';
import dayjs from 'dayjs/esm';
import { Observable } from 'rxjs';

import { map } from 'rxjs/operators';

export type EntityResponseType = HttpResponse<ProgrammingExercise>;
export type EntityArrayResponseType = HttpResponse<ProgrammingExercise[]>;

/** the programming exercise sharing service */
@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseSharingService {

    protected readonly baseSharingConfigUrl = 'api/core/sharing/config';
    protected readonly resourceUrl = 'api/programming/sharing/import';
    protected readonly resourceUrlBasket = 'api/programming/sharing/import/basket/';
    protected readonly resourceUrlExport = 'api/programming/sharing/export';
    protected readonly resourceUrlSetupImport = 'api/programming/sharing/setup-import';

    private readonly http = inject(HttpClient);
    private readonly profileService = inject(ProfileService);

    /**
     * loads the Shopping Basket via the Service
     */
    getSharedExercises(sharingInfo: SharingInfo): Observable<ShoppingBasket> {
        // Validate required parameters
        if (!sharingInfo.basketToken || !sharingInfo.apiBaseURL || !sharingInfo.checksum || !sharingInfo.returnURL) {
            throw new Error('Missing required sharing information: basketToken, apiBaseURL, sharingInfo.checksum and sharingInfo.returnURL are required');
        }
        return this.http
            .get<ShoppingBasket>(this.resourceUrl + '/basket', {
                params: { basketToken: sharingInfo.basketToken, returnURL: sharingInfo.returnURL, apiBaseURL: sharingInfo.apiBaseURL, checksum: sharingInfo.checksum },
                observe: 'response',
            })
            .pipe(map((response: HttpResponse<ShoppingBasket>) => response.body!));
    }

    loadDetailsForExercises(sharingInfo: SharingInfo): Observable<ProgrammingExercise> {
        return this.http
            .post<ProgrammingExercise>(this.resourceUrlBasket + 'exercise-details', sharingInfo, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res).body!));
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
            .post<ProgrammingExercise>(this.resourceUrlSetupImport, { exercise: copy, course, sharingInfo }, { observe: 'response' })
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
            const { exercise: _ignoredExercise, results: _ignoredResults, submissions: _ignoredSubmissions, ...filteredTemplateParticipation } = copy.templateParticipation as any;
            copy.templateParticipation = { ...filteredTemplateParticipation } as TemplateProgrammingExerciseParticipation;
        }
        if (copy.solutionParticipation) {
            const { exercise: _ignoredExercise, results: _ignoredResults, submissions: _ignoredSubmissions, ...filteredSolutionParticipation } = copy.solutionParticipation as any;
            copy.solutionParticipation = { ...filteredSolutionParticipation } as SolutionProgrammingExerciseParticipation;
        }

        copy.categories = ExerciseService.stringifyExerciseCategories(copy);

        return copy as ProgrammingExercise;
    }

    /**
     * Convert all date fields of the programming exercise to dayjs date objects.
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
    exportProgrammingExerciseToSharing(programmingExerciseId: number, callBackUrl: string): Observable<HttpResponse<string>> {
        return this.http.post<string>(this.resourceUrlExport + `/${programmingExerciseId}`, callBackUrl, {
            observe: 'response',
        });
    }

    /**
     * Check if the Sharing platform integration has been enabled.
     * If enabled the request will return a 200  and true or false, and a 503 if not.
     */
    isSharingEnabled(): Observable<boolean> {
        if (!this.profileService.isModuleFeatureActive(MODULE_FEATURE_SHARING)) {
            return of(false);
        }
        return this.http
            .get<boolean>(`${this.baseSharingConfigUrl}/is-enabled`, { observe: 'response' })
            .pipe(map(response => response.body ?? false));
    }
}
