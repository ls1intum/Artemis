import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { convertDateFromServer } from 'app/utils/date.utils';
import { map } from 'rxjs/operators';
import { TutorialGroupSession } from 'app/entities/TutorialGroupSession.model';

type EntityArrayResponseType = HttpResponse<TutorialGroupSession[]>;

@Injectable({ providedIn: 'root' })
export class TutorialGroupSessionService {
    private resourceURL = SERVER_API_URL + 'api';

    constructor(private httpClient: HttpClient) {}

    getSessions(courseId: number, tutorialGroupId: number): Observable<EntityArrayResponseType> {
        return this.httpClient
            .get<TutorialGroupSession[]>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertTutorialGroupSessionsResponseArrayDatesFromServer(res)));
    }

    private convertTutorialGroupSessionDatesFromServer(tutorialGroupSession: TutorialGroupSession): TutorialGroupSession {
        tutorialGroupSession.start = convertDateFromServer(tutorialGroupSession.start);
        tutorialGroupSession.end = convertDateFromServer(tutorialGroupSession.end);
        return tutorialGroupSession;
    }

    private convertTutorialGroupSessionsResponseArrayDatesFromServer(res: HttpResponse<TutorialGroupSession[]>): HttpResponse<TutorialGroupSession[]> {
        if (res.body) {
            res.body.forEach((tutorialGroupSession: TutorialGroupSession) => {
                this.convertTutorialGroupSessionDatesFromServer(tutorialGroupSession);
            });
        }
        return res;
    }
}
