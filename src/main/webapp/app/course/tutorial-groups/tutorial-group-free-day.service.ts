import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { Observable } from 'rxjs';
import { convertDateFromServer } from 'app/utils/date.utils';
import { map } from 'rxjs/operators';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/tutorial-group-session.service';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/tutorial-groups-configuration.service';
import { TutorialGroupFreeDay } from 'app/entities/tutorial-group/tutorial-group-free-day.model';

type EntityResponseType = HttpResponse<TutorialGroupFreeDay>;

@Injectable({ providedIn: 'root' })
export class TutorialGroupFreeDayService {
    private resourceURL = SERVER_API_URL + 'api';

    constructor(
        private httpClient: HttpClient,
        private tutorialGroupSessionService: TutorialGroupSessionService,
        private tutorialGroupsConfigurationService: TutorialGroupsConfigurationService,
    ) {}

    create(tutorialGroupConfigurationId: number, tutorialGroupFreeDay: TutorialGroupFreeDay): Observable<EntityResponseType> {
        const copy = this.convertTutorialGroupFreeDayDatesFromClient(tutorialGroupFreeDay);
        return this.httpClient
            .post<TutorialGroupFreeDay>(`${this.resourceURL}/tutorial-groups-configurations/${tutorialGroupConfigurationId}/tutorial-free-days`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupFreeDayResponseDatesFromServer(res)));
    }

    delete(tutorialGroupFreeDayId: number): Observable<HttpResponse<void>> {
        return this.httpClient.delete<void>(`${this.resourceURL}/tutorial-group-free-days/${tutorialGroupFreeDayId}`, { observe: 'response' });
    }

    private convertTutorialGroupDatesFromServer(tutorialGroup: TutorialGroup): TutorialGroup {
        if (tutorialGroup.tutorialGroupSchedule) {
            tutorialGroup.tutorialGroupSchedule.validFromInclusive = convertDateFromServer(tutorialGroup.tutorialGroupSchedule.validFromInclusive);
            tutorialGroup.tutorialGroupSchedule.validToInclusive = convertDateFromServer(tutorialGroup.tutorialGroupSchedule.validToInclusive);
        }
        return tutorialGroup;
    }

    private convertTutorialGroupFreeDayResponseDatesFromServer(res: HttpResponse<TutorialGroupFreeDay>): HttpResponse<TutorialGroupFreeDay> {
        if (res.body) {
            res.body.date = convertDateFromServer(res.body.date);
        }
        return res;
    }

    private convertTutorialGroupResponseArrayDatesFromServer(res: HttpResponse<TutorialGroup[]>): HttpResponse<TutorialGroup[]> {
        if (res.body) {
            res.body.forEach((tutorialGroup: TutorialGroup) => {
                this.convertTutorialGroupDatesFromServer(tutorialGroup);
            });
        }
        return res;
    }

    private convertTutorialGroupFreeDayDatesFromClient(tutorialGroupFreeDay: TutorialGroupFreeDay): TutorialGroupFreeDay {
        if (tutorialGroupFreeDay) {
            return Object.assign({}, tutorialGroupFreeDay, {
                date: tutorialGroupFreeDay.date!.format('YYYY-MM-DD'),
            });
        } else {
            return tutorialGroupFreeDay;
        }
    }
}
