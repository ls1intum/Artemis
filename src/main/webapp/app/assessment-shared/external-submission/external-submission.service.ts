import { Injectable } from '@angular/core';
import { EntityResponseType, Result, ResultService } from 'app/entities/result';
import { SERVER_API_URL } from 'app/app.constants';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import * as moment from 'moment';
import { Exercise } from 'app/entities/exercise';
import { User } from 'app/core/user/user.model';

@Injectable({ providedIn: 'root' })
export class ExternalSubmissionService {
    // TODO: It would be good to refactor the convertDate methods into a separate service, so that we don't have to import the result service here.
    constructor(private http: HttpClient, private resultService: ResultService) {}

    create(exercise: Exercise, student: User, result: Result): Observable<EntityResponseType> {
        const copy = this.resultService.convertDateFromClient(result);
        return this.http
            .post<Result>(`${SERVER_API_URL}api/exercises/${exercise.id}/external-submission-results?studentLogin=${student.login}`, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.resultService.convertDateFromServer(res));
    }

    generateInitialManualResult() {
        const newResult = new Result();
        newResult.completionDate = moment();
        newResult.successful = true;
        newResult.score = 100;
        newResult.rated = true;
        return newResult;
    }
}
