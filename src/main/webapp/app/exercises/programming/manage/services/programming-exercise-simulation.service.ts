import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { EntityResponseType, ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { HttpClient } from '@angular/common/http';
import { map } from 'rxjs/operators';
import { Observable } from 'rxjs/Observable';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';

/**
 *
 * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
 */
@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseSimulationService {
    public resourceUrl = SERVER_API_URL + 'api/programming-exercises';

    constructor(private http: HttpClient, private programmingExerciseService: ProgrammingExerciseService, private profileService: ProfileService) {}

    /**
     * Triggers the creation and setup of a new programming exercise without connection
     * to the VCS and CI
     * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
     * @param programmingExercise
     */
    automaticSetupWithoutConnectionToVCSandCI(programmingExercise: ProgrammingExercise): Observable<EntityResponseType> {
        this.failsIfInProduction();
        const copy = this.programmingExerciseService.convertDataFromClient(programmingExercise);
        return this.http
            .post<ProgrammingExercise>(this.resourceUrl + '/no-vcs-and-ci-available', copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.programmingExerciseService.convertDateFromServer(res)));
    }

    /**
     * Checks if the current environment is production, if yes the method throws an error
     * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
     * It should prevent developers from misusing methods, which should be only used for testing
     */
    failsIfInProduction() {
        if (this.profileService.isInProduction()) {
            alert('This action is NOT supported on production and should NOT be visible. Please contact a developer immediately!');
            throw new Error('This action is NOT supported on production and should NOT be visible. Please contact a developer immediately!');
        } else {
        }
    }
}
