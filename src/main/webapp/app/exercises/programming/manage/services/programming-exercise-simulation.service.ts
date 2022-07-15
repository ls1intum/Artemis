import { Injectable } from '@angular/core';
import { EntityResponseType, ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { HttpClient } from '@angular/common/http';
import { map } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

/**
 *
 * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
 */
@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseSimulationService {
    public resourceUrl = SERVER_API_URL + 'api/programming-exercises';

    constructor(private http: HttpClient, private programmingExerciseService: ProgrammingExerciseService) {}

    /**
     * Triggers the creation and setup of a new programming exercise without connection
     * to the VCS and CI
     * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
     * @param programmingExercise
     */
    automaticSetupWithoutConnectionToVCSandCI(programmingExercise: ProgrammingExercise): Observable<EntityResponseType> {
        let copy = this.programmingExerciseService.convertDataFromClient(programmingExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        return this.http
            .post<ProgrammingExercise>(this.resourceUrl + '/no-vcs-and-ci-available', copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => ProgrammingExerciseService.convertProgrammingExerciseResponseDatesFromServer(res)));
    }

    /**
     * Checks if the current environment is production, if yes the method throws an error
     * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
     * It should prevent developers from misusing methods, which should be only used for testing
     */
    failsIfInProduction(isInProduction: boolean) {
        if (isInProduction) {
            alert('This action is NOT supported on production and should NOT be visible. Please contact a developer immediately!');
            throw new Error('This action is NOT supported on production and should NOT be visible. Please contact a developer immediately!');
        }
    }
}
