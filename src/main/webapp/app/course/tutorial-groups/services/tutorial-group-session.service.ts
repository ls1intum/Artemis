import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { convertDateFromServer, toISO8601DateString } from 'app/utils/date.utils';
import { map } from 'rxjs/operators';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';

type EntityResponseType = HttpResponse<TutorialGroupSession>;

export class TutorialGroupSessionDTO {
    public date?: Date;
    public startTime?: string;
    public endTime?: string;
    public location?: string;
}

@Injectable({ providedIn: 'root' })
export class TutorialGroupSessionService {
    private resourceURL = SERVER_API_URL + 'api';

    constructor(private httpClient: HttpClient) {}
    getOneOfTutorialGroup(courseId: number, tutorialGroupId: number, sessionId: number) {
        return this.httpClient
            .get<TutorialGroup>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions/${sessionId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupSessionResponseDatesFromServer(res)));
    }

    update(courseId: number, tutorialGroupId: number, sessionId: number, session: TutorialGroupSessionDTO): Observable<EntityResponseType> {
        const copy = this.convertTutorialGroupSessionDatesFromClient(session);
        return this.httpClient
            .put<TutorialGroup>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions/${sessionId}`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupSessionResponseDatesFromServer(res)));
    }

    create(courseId: number, tutorialGroupId: number, session: TutorialGroupSessionDTO): Observable<EntityResponseType> {
        const copy = this.convertTutorialGroupSessionDatesFromClient(session);
        return this.httpClient
            .post<TutorialGroupSession>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupSessionResponseDatesFromServer(res)));
    }

    cancel(courseId: number, tutorialGroupId: number, sessionId: number, explanation: string): Observable<EntityResponseType> {
        return this.httpClient
            .post<TutorialGroupSession>(
                `${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions/${sessionId}/cancel`,
                { status_explanation: explanation },
                { observe: 'response' },
            )
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupSessionResponseDatesFromServer(res)));
    }

    activate(courseId: number, tutorialGroupId: number, sessionId: number): Observable<EntityResponseType> {
        return this.httpClient
            .post<TutorialGroupSession>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions/${sessionId}/activate`, {}, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupSessionResponseDatesFromServer(res)));
    }

    delete(courseId: number, tutorialGroupId: number, sessionId: number): Observable<HttpResponse<void>> {
        return this.httpClient.delete<void>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions/${sessionId}`, { observe: 'response' });
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

    private convertTutorialGroupSessionDatesFromClient(tutorialGroupSessionDTO: TutorialGroupSessionDTO): TutorialGroupSessionDTO {
        if (tutorialGroupSessionDTO) {
            return Object.assign({}, tutorialGroupSessionDTO, {
                date: toISO8601DateString(tutorialGroupSessionDTO.date),
            });
        } else {
            return tutorialGroupSessionDTO;
        }
    }
}
