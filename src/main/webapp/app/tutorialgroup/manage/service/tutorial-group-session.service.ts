import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { CreateOrUpdateTutorialGroupSessionDTO, TutorialGroupSession, TutorialGroupSessionDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroupSessionApiService } from 'app/openapi/api/tutorialGroupSessionApi.service';
import { convertTutorialGroupSessionDatesFromServer } from 'app/tutorialgroup/shared/util/convertTutorialGroupEntityDates';

type EntityResponseType = HttpResponse<TutorialGroupSession>;

@Injectable({ providedIn: 'root' })
export class TutorialGroupSessionService {
    private httpClient = inject(HttpClient);
    private tutorialGroupSessionApiService = inject(TutorialGroupSessionApiService);

    private resourceURL = 'api/tutorialgroup';

    update(courseId: number, tutorialGroupId: number, sessionId: number, updateTutorialGroupSessionDTO: CreateOrUpdateTutorialGroupSessionDTO): Observable<void> {
        return this.httpClient.put<void>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions/${sessionId}`, updateTutorialGroupSessionDTO);
    }

    create(courseId: number, tutorialGroupId: number, createTutorialGroupSessionDTO: CreateOrUpdateTutorialGroupSessionDTO): Observable<TutorialGroupSessionDTO> {
        return this.httpClient.post<TutorialGroupSessionDTO>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions`, createTutorialGroupSessionDTO);
    }

    delete(courseId: number, tutorialGroupId: number, sessionId: number): Observable<HttpResponse<void>> {
        return this.tutorialGroupSessionApiService.deleteSession(courseId, tutorialGroupId, sessionId, 'response');
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

    private convertTutorialGroupSessionResponseDatesFromServer(res: HttpResponse<TutorialGroupSession>): HttpResponse<TutorialGroupSession> {
        if (res.body) {
            convertTutorialGroupSessionDatesFromServer(res.body);
        }
        return res;
    }
}
