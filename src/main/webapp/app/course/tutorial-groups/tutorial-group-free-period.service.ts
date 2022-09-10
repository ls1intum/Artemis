import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { convertDateFromServer, toISO8601DateString } from 'app/utils/date.utils';
import { map } from 'rxjs/operators';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/tutorial-group-session.service';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/tutorial-groups-configuration.service';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { TutorialGroupDateUtilsService } from 'app/course/tutorial-groups/util/tutorial-group-date-utils.service';

type EntityResponseType = HttpResponse<TutorialGroupFreePeriod>;

export class TutorialGroupFreePeriodDTO {
    public date?: Date;
    public reason?: string;
}

@Injectable({ providedIn: 'root' })
export class TutorialGroupFreePeriodService {
    private resourceURL = SERVER_API_URL + 'api';

    constructor(
        private httpClient: HttpClient,
        private tutorialGroupSessionService: TutorialGroupSessionService,
        private tutorialGroupsConfigurationService: TutorialGroupsConfigurationService,
        private tutorialGroupDateUtilsService: TutorialGroupDateUtilsService,
    ) {}
    create(tutorialGroupConfigurationId: number, tutorialGroupFreePeriodDTO: TutorialGroupFreePeriodDTO): Observable<EntityResponseType> {
        const copy = this.convertTutorialGroupFreePeriodDatesFromClient(tutorialGroupFreePeriodDTO);
        return this.httpClient
            .post<TutorialGroupFreePeriod>(`${this.resourceURL}/tutorial-groups-configurations/${tutorialGroupConfigurationId}/tutorial-free-periods`, copy, {
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupFreePeriodResponseDatesFromServer(res)));
    }

    delete(tutorialGroupFreePeriodId: number): Observable<HttpResponse<void>> {
        return this.httpClient.delete<void>(`${this.resourceURL}/tutorial-group-free-periods/${tutorialGroupFreePeriodId}`, { observe: 'response' });
    }

    convertTutorialGroupFreePeriodDatesFromServer(tutorialGroupFreePeriod: TutorialGroupFreePeriod): TutorialGroupFreePeriod {
        tutorialGroupFreePeriod.start = convertDateFromServer(tutorialGroupFreePeriod.start);
        tutorialGroupFreePeriod.end = convertDateFromServer(tutorialGroupFreePeriod.end);
        return tutorialGroupFreePeriod;
    }

    private convertTutorialGroupFreePeriodResponseDatesFromServer(res: HttpResponse<TutorialGroupFreePeriod>): HttpResponse<TutorialGroupFreePeriod> {
        if (res.body) {
            this.convertTutorialGroupFreePeriodDatesFromServer(res.body);
        }
        return res;
    }

    private convertTutorialGroupFreePeriodDatesFromClient(tutorialGroupFreePeriodDTO: TutorialGroupFreePeriodDTO): TutorialGroupFreePeriodDTO {
        if (tutorialGroupFreePeriodDTO) {
            return Object.assign({}, tutorialGroupFreePeriodDTO, {
                date: toISO8601DateString(tutorialGroupFreePeriodDTO.date),
            });
        } else {
            return tutorialGroupFreePeriodDTO;
        }
    }
}
