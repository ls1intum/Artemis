import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { TutorialGroupSession, TutorialGroupSessionDTO, TutorialGroupSessionRequestDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroupSessionApiService } from 'app/openapi/api/tutorialGroupSessionApi.service';
import { TutorialGroupFreePeriodService } from 'app/tutorialgroup/shared/service/tutorial-group-free-period.service';
import { convertDateFromServer } from 'app/shared/util/date.utils';

type EntityResponseType = HttpResponse<TutorialGroupSessionDTO>;

@Injectable({ providedIn: 'root' })
export class TutorialGroupSessionService {
    private httpClient = inject(HttpClient);
    private tutorialGroupFreePeriodService = inject(TutorialGroupFreePeriodService);
    private tutorialGroupSessionApiService = inject(TutorialGroupSessionApiService);

    private resourceURL = 'api/tutorialgroup';

    getOneOfTutorialGroup(courseId: number, tutorialGroupId: number, sessionId: number) {
        return this.httpClient.get<TutorialGroupSessionDTO>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions/${sessionId}`, {
            observe: 'response',
        });
    }

    update(courseId: number, tutorialGroupId: number, sessionId: number, session: TutorialGroupSessionRequestDTO): Observable<EntityResponseType> {
        return this.httpClient.put<TutorialGroupSessionDTO>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions/${sessionId}`, session, {
            observe: 'response',
        });
    }

    updateAttendanceCount(courseId: number, tutorialGroupId: number, sessionId: number, attendanceCount: number | undefined): Observable<EntityResponseType> {
        let params = new HttpParams();
        if (attendanceCount !== undefined && attendanceCount !== null) {
            params = params.append('attendanceCount', attendanceCount.toString());
        }
        return this.httpClient.patch<TutorialGroupSessionDTO>(
            `${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions/${sessionId}/attendance-count`,
            null,
            {
                observe: 'response',
                params,
            },
        );
    }

    create(courseId: number, tutorialGroupId: number, session: TutorialGroupSessionRequestDTO): Observable<EntityResponseType> {
        return this.httpClient.post<TutorialGroupSessionDTO>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions`, session, {
            observe: 'response',
        });
    }

    cancel(courseId: number, tutorialGroupId: number, sessionId: number, explanation?: string): Observable<EntityResponseType> {
        return this.httpClient.post<TutorialGroupSessionDTO>(
            `${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions/${sessionId}/cancel`,
            { status_explanation: explanation },
            { observe: 'response' },
        );
    }

    activate(courseId: number, tutorialGroupId: number, sessionId: number): Observable<EntityResponseType> {
        return this.httpClient.post<TutorialGroupSessionDTO>(
            `${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions/${sessionId}/activate`,
            {},
            { observe: 'response' },
        );
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
}
