import { IProgrammingSubmissionService } from 'app/programming-submission';
import { Injectable } from '@angular/core';
import { EntityResponseType, Result, ResultService } from 'app/entities/result';
import { SERVER_API_URL } from 'app/app.constants';
import { Observable } from 'rxjs';
import { StudentParticipation } from 'app/entities/participation';
import { HttpClient } from '@angular/common/http';
import * as moment from 'moment';

@Injectable({ providedIn: 'root' })
export class ProgrammingAssessmentManualResultService {
    // TODO: It would be good to refactor the convertDate methods into a separate service, so that we don't have to import the result service here.
    constructor(private http: HttpClient, private resultService: ResultService) {}

    create(result: Result): Observable<EntityResponseType> {
        const copy = this.resultService.convertDateFromClient(result);
        // NOTE: we deviate from the standard URL scheme to avoid conflicts with a different POST request on results
        return this.http
            .post<Result>(SERVER_API_URL + 'api/manual-results', copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.resultService.convertDateFromServer(res));
    }

    update(result: Result): Observable<EntityResponseType> {
        const copy = this.resultService.convertDateFromClient(result);
        return this.http
            .put<Result>(SERVER_API_URL + 'api/manual-results', copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.resultService.convertDateFromServer(res));
    }

    generateInitialManualResult() {
        const newResult = new Result();
        newResult.completionDate = moment();
        newResult.successful = true;
        newResult.score = 100;
        return newResult;
    }
}
