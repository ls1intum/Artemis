import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { convertDateFromClient, convertDateFromServer } from 'app/utils/date.utils';
import { map } from 'rxjs/operators';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';

type EntityArrayResponseType = HttpResponse<TutorialGroupSession[]>;
type EntityResponseType = HttpResponse<TutorialGroupSession>;

@Injectable({ providedIn: 'root' })
export class TutorialGroupSessionService {
    private resourceURL = SERVER_API_URL + 'api';

    constructor(private httpClient: HttpClient) {}

    create(tutorialGroupId: number, tutorialGroupSession: TutorialGroupSession): Observable<EntityResponseType> {
        const copy = this.convertTutorialGroupSessionDatesFromClient(tutorialGroupSession);
        return this.httpClient
            .post<TutorialGroupSession>(`${this.resourceURL}/tutorial-groups/${tutorialGroupId}/tutorial-group-sessions`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupSessionResponseDatesFromServer(res)));
    }

    cancel(tutorialGroupSessionId: number, explanation: string): Observable<EntityResponseType> {
        return this.httpClient
            .post<TutorialGroupSession>(
                `${this.resourceURL}/tutorial-group-sessions/${tutorialGroupSessionId}/cancel`,
                { status_explanation: explanation },
                { observe: 'response' },
            )
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupSessionResponseDatesFromServer(res)));
    }

    activate(tutorialGroupSessionId: number): Observable<EntityResponseType> {
        return this.httpClient
            .post<TutorialGroupSession>(`${this.resourceURL}/tutorial-group-sessions/${tutorialGroupSessionId}/activate`, {}, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupSessionResponseDatesFromServer(res)));
    }

    getSessions(courseId: number, tutorialGroupId: number): Observable<EntityArrayResponseType> {
        return this.httpClient
            .get<TutorialGroupSession[]>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertTutorialGroupSessionsResponseArrayDatesFromServer(res)));
    }

    convertTutorialGroupSessionDatesFromServer(tutorialGroupSession: TutorialGroupSession): TutorialGroupSession {
        tutorialGroupSession.start = convertDateFromServer(tutorialGroupSession.start);
        tutorialGroupSession.end = convertDateFromServer(tutorialGroupSession.end);
        return tutorialGroupSession;
    }

    private convertTutorialGroupSessionResponseDatesFromServer(res: HttpResponse<TutorialGroupSession>): HttpResponse<TutorialGroupSession> {
        if (res.body) {
            this.convertTutorialGroupSessionDatesFromServer(res.body);
        }
        return res;
    }

    private convertTutorialGroupSessionsResponseArrayDatesFromServer(res: HttpResponse<TutorialGroupSession[]>): HttpResponse<TutorialGroupSession[]> {
        if (res.body) {
            res.body.forEach((tutorialGroupSession: TutorialGroupSession) => {
                this.convertTutorialGroupSessionDatesFromServer(tutorialGroupSession);
            });
        }
        return res;
    }

    private convertTutorialGroupSessionDatesFromClient(tutorialGroupSession: TutorialGroupSession): TutorialGroupSession {
        if (tutorialGroupSession) {
            return Object.assign({}, tutorialGroupSession, {
                start: convertDateFromClient(tutorialGroupSession.start),
                end: convertDateFromClient(tutorialGroupSession.end),
            });
        } else {
            return tutorialGroupSession;
        }
    }
}
