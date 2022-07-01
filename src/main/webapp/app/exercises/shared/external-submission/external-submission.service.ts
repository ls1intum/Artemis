import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { Exercise } from 'app/entities/exercise.model';
import { User } from 'app/core/user/user.model';
import { EntityResponseType, ResultService } from 'app/exercises/shared/result/result.service';
import { Result } from 'app/entities/result.model';
import { map } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class ExternalSubmissionService {
    // TODO: It would be good to refactor the convertDate methods into a separate service, so that we don't have to import the result service here.
    constructor(private http: HttpClient, private resultService: ResultService) {}

    /**
     * Persist a new result for the provided exercise and student (a participation and an empty submission will also be created if they do not exist yet)
     * @param { Exercise } exercise - Exercise for which a new result is created
     * @param { User } student - Student for whom a result is created
     * @param { Result } result - Result that is added
     */
    create(exercise: Exercise, student: User, result: Result): Observable<EntityResponseType> {
        const copy = this.resultService.convertResultDatesFromClient(result);
        return this.http
            .post<Result>(`${SERVER_API_URL}api/exercises/${exercise.id}/external-submission-results?studentLogin=${student.login}`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.resultService.convertResultResponseDatesFromServer(res)));
    }

    /**
     * Generates an initial manual result with default values.
     */
    generateInitialManualResult() {
        const newResult = new Result();
        newResult.completionDate = dayjs();
        newResult.successful = true;
        newResult.score = 100;
        newResult.rated = true;
        return newResult;
    }
}
