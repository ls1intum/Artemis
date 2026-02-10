import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { convertDateFromServer, toISO8601DateString } from 'app/shared/util/date.utils';
import { map } from 'rxjs/operators';
import { TutorialGroupSessionDTO, TutorialGroupSessionRequestDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroupFreePeriodService } from 'app/tutorialgroup/shared/service/tutorial-group-free-period.service';
import { TutorialGroupSessionApiService } from 'app/openapi/api/tutorialGroupSessionApi.service';

type EntityResponseType = HttpResponse<TutorialGroupSessionDTO>;

@Injectable({ providedIn: 'root' })
export class TutorialGroupSessionService {
    private httpClient = inject(HttpClient);
    private tutorialGroupFreePeriodService = inject(TutorialGroupFreePeriodService);
    private tutorialGroupSessionApiService = inject(TutorialGroupSessionApiService);

    private resourceURL = 'api/tutorialgroup';

    getOneOfTutorialGroup(courseId: number, tutorialGroupId: number, sessionId: number) {
        return this.httpClient
            .get<TutorialGroupSessionDTO>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions/${sessionId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupSessionResponseDatesFromServer(res)));
    }

    update(courseId: number, tutorialGroupId: number, sessionId: number, session: TutorialGroupSessionRequestDTO): Observable<EntityResponseType> {
        const copy = this.convertTutorialGroupSessionDatesFromClient(session);
        return this.httpClient
            .put<TutorialGroupSessionDTO>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions/${sessionId}`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupSessionResponseDatesFromServer(res)));
    }

    updateAttendanceCount(courseId: number, tutorialGroupId: number, sessionId: number, attendanceCount: number | undefined): Observable<EntityResponseType> {
        let params = new HttpParams();
        if (attendanceCount !== undefined && attendanceCount !== null) {
            params = params.append('attendanceCount', attendanceCount.toString());
        }
        return this.httpClient
            .patch<TutorialGroupSessionDTO>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions/${sessionId}/attendance-count`, null, {
                observe: 'response',
                params,
            })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupSessionResponseDatesFromServer(res)));
    }

    create(courseId: number, tutorialGroupId: number, session: TutorialGroupSessionRequestDTO): Observable<EntityResponseType> {
        const copy = this.convertTutorialGroupSessionDatesFromClient(session);
        return this.httpClient
            .post<TutorialGroupSessionDTO>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupSessionResponseDatesFromServer(res)));
    }

    cancel(courseId: number, tutorialGroupId: number, sessionId: number, explanation: string): Observable<EntityResponseType> {
        return this.httpClient
            .post<TutorialGroupSessionDTO>(
                `${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions/${sessionId}/cancel`,
                { status_explanation: explanation },
                { observe: 'response' },
            )
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupSessionResponseDatesFromServer(res)));
    }

    activate(courseId: number, tutorialGroupId: number, sessionId: number): Observable<EntityResponseType> {
        return this.httpClient
            .post<TutorialGroupSessionDTO>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions/${sessionId}/activate`, {}, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupSessionResponseDatesFromServer(res)));
    }

    delete(courseId: number, tutorialGroupId: number, sessionId: number): Observable<HttpResponse<void>> {
        return this.tutorialGroupSessionApiService.deleteSession(courseId, tutorialGroupId, sessionId, 'response');
    }

    convertTutorialGroupSessionDatesFromServer(tutorialGroupSessionDTO: TutorialGroupSessionDTO): TutorialGroupSessionDTO {
        tutorialGroupSessionDTO.startDate = convertDateFromServer(tutorialGroupSessionDTO.startDate);
        tutorialGroupSessionDTO.endDate = convertDateFromServer(tutorialGroupSessionDTO.endDate);
        if (tutorialGroupSessionDTO.freePeriod) {
            tutorialGroupSessionDTO.freePeriod = this.tutorialGroupFreePeriodService.convertTutorialGroupFreePeriodDatesFromServer(tutorialGroupSessionDTO.freePeriod);
        }
        return tutorialGroupSessionDTO;
    }

    private convertTutorialGroupSessionResponseDatesFromServer(res: HttpResponse<TutorialGroupSessionDTO>): HttpResponse<TutorialGroupSessionDTO> {
        if (res.body) {
            this.convertTutorialGroupSessionDatesFromServer(res.body);
        }
        return res;
    }

    private convertTutorialGroupSessionDatesFromClient(tutorialGroupSessionRequestDTO: TutorialGroupSessionRequestDTO): TutorialGroupSessionRequestDTO {
        if (tutorialGroupSessionRequestDTO) {
            return Object.assign({}, tutorialGroupSessionRequestDTO, {
                date: toISO8601DateString(tutorialGroupSessionRequestDTO.date),
            });
        } else {
            return tutorialGroupSessionRequestDTO;
        }
    }
}
