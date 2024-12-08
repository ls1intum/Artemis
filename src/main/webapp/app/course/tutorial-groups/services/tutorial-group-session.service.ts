import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { convertDateFromServer, toISO8601DateString } from 'app/utils/date.utils';
import { map } from 'rxjs/operators';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroupFreePeriodService } from 'app/course/tutorial-groups/services/tutorial-group-free-period.service';

type EntityResponseType = HttpResponse<TutorialGroupSession>;

export class TutorialGroupSessionDTO {
    public date?: Date;
    public startTime?: string;
    public endTime?: string;
    public location?: string;
}

@Injectable({ providedIn: 'root' })
export class TutorialGroupSessionService {
    private httpClient = inject(HttpClient);
    private tutorialGroupFreePeriodService = inject(TutorialGroupFreePeriodService);

    private resourceURL = 'api';

    getOneOfTutorialGroup(courseId: number, tutorialGroupId: number, sessionId: number) {
        return this.httpClient
            .get<TutorialGroupSession>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions/${sessionId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupSessionResponseDatesFromServer(res)));
    }

    update(courseId: number, tutorialGroupId: number, sessionId: number, session: TutorialGroupSessionDTO): Observable<EntityResponseType> {
        const copy = this.convertTutorialGroupSessionDatesFromClient(session);
        return this.httpClient
            .put<TutorialGroupSession>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions/${sessionId}`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupSessionResponseDatesFromServer(res)));
    }

    updateAttendanceCount(courseId: number, tutorialGroupId: number, sessionId: number, attendanceCount: number | undefined): Observable<EntityResponseType> {
        let params = new HttpParams();
        if (attendanceCount !== undefined && attendanceCount !== null) {
            params = params.append('attendanceCount', attendanceCount.toString());
        }
        return this.httpClient
            .patch<TutorialGroupSession>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions/${sessionId}/attendance-count`, null, {
                observe: 'response',
                params,
            })
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
        if (tutorialGroupSession.tutorialGroupFreePeriod) {
            tutorialGroupSession.tutorialGroupFreePeriod = this.tutorialGroupFreePeriodService.convertTutorialGroupFreePeriodDatesFromServer(
                tutorialGroupSession.tutorialGroupFreePeriod,
            );
        }
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
