import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { EntityResponseType, ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { HttpClient } from '@angular/common/http';
import { map } from 'rxjs/operators';
import { Observable } from 'rxjs/Observable';

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
        const copy = this.programmingExerciseService.convertDataFromClient(programmingExercise);
        return this.http
            .post<ProgrammingExercise>(this.resourceUrl + '/no-local-setup', copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.programmingExerciseService.convertDateFromServer(res)));
    }
}
