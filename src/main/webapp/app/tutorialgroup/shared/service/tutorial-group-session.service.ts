import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { convertDateFromServer } from 'app/shared/util/date.utils';
import { map } from 'rxjs/operators';
import { CreateOrUpdateTutorialGroupSessionDTO, TutorialGroupSession, TutorialGroupSessionDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroupFreePeriodService } from 'app/tutorialgroup/shared/service/tutorial-group-free-period.service';
import { TutorialGroupSessionApiService } from 'app/openapi/api/tutorialGroupSessionApi.service';

type EntityResponseType = HttpResponse<TutorialGroupSession>;

@Injectable({ providedIn: 'root' })
export class TutorialGroupSessionService {
    private httpClient = inject(HttpClient);
    private tutorialGroupFreePeriodService = inject(TutorialGroupFreePeriodService);
    private tutorialGroupSessionApiService = inject(TutorialGroupSessionApiService);

    private resourceURL = 'api/tutorialgroup';

    getOneOfTutorialGroup(courseId: number, tutorialGroupId: number, sessionId: number) {
        return this.httpClient
            .get<TutorialGroupSession>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions/${sessionId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupSessionResponseDatesFromServer(res)));
    }

    update(courseId: number, tutorialGroupId: number, sessionId: number, updateTutorialGroupSessionDTO: CreateOrUpdateTutorialGroupSessionDTO): Observable<void> {
        return this.httpClient.put<void>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions/${sessionId}`, updateTutorialGroupSessionDTO);
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

    create(courseId: number, tutorialGroupId: number, createTutorialGroupSessionDTO: CreateOrUpdateTutorialGroupSessionDTO): Observable<TutorialGroupSessionDTO> {
        return this.httpClient.post<TutorialGroupSessionDTO>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions`, createTutorialGroupSessionDTO);
    }

    cancel(courseId: number, tutorialGroupId: number, sessionId: number, explanation?: string): Observable<EntityResponseType> {
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
        return this.tutorialGroupSessionApiService.deleteSession(courseId, tutorialGroupId, sessionId, 'response');
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
}
