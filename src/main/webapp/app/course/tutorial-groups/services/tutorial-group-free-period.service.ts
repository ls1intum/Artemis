import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { convertDateFromServer, toISO8601DateTimeString } from 'app/utils/date.utils';
import { map } from 'rxjs/operators';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';

type EntityResponseType = HttpResponse<TutorialGroupFreePeriod>;

export class TutorialGroupFreePeriodDTO {
    public startDate?: Date;
    public endDate?: Date;
    public reason?: string;
}

@Injectable({ providedIn: 'root' })
export class TutorialGroupFreePeriodService {
    private httpClient = inject(HttpClient);

    private resourceURL = 'api';

    getOneOfConfiguration(courseId: number, tutorialGroupsConfigurationId: number, tutorialGroupFreePeriodId: number): Observable<EntityResponseType> {
        return this.httpClient
            .get<TutorialGroupFreePeriod>(
                `${this.resourceURL}/courses/${courseId}/tutorial-groups-configuration/${tutorialGroupsConfigurationId}/tutorial-free-periods/${tutorialGroupFreePeriodId}`,
                { observe: 'response' },
            )
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupFreePeriodResponseDatesFromServer(res)));
    }

    create(courseId: number, tutorialGroupConfigurationId: number, tutorialGroupFreePeriodDTO: TutorialGroupFreePeriodDTO): Observable<EntityResponseType> {
        const copy = this.convertTutorialGroupFreePeriodDatesFromClient(tutorialGroupFreePeriodDTO);
        return this.httpClient
            .post<TutorialGroupFreePeriod>(`${this.resourceURL}/courses/${courseId}/tutorial-groups-configuration/${tutorialGroupConfigurationId}/tutorial-free-periods`, copy, {
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupFreePeriodResponseDatesFromServer(res)));
    }

    update(
        courseId: number,
        tutorialGroupConfigurationId: number,
        tutorialGroupFreePeriodId: number,
        tutorialGroupFreePeriodDTO: TutorialGroupFreePeriodDTO,
    ): Observable<EntityResponseType> {
        const copy = this.convertTutorialGroupFreePeriodDatesFromClient(tutorialGroupFreePeriodDTO);
        return this.httpClient
            .put<TutorialGroupFreePeriod>(
                `${this.resourceURL}/courses/${courseId}/tutorial-groups-configuration/${tutorialGroupConfigurationId}/tutorial-free-periods/${tutorialGroupFreePeriodId}`,
                copy,
                {
                    observe: 'response',
                },
            )
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupFreePeriodResponseDatesFromServer(res)));
    }

    delete(courseId: number, tutorialGroupConfigurationId: number, tutorialGroupFreePeriodId: number): Observable<HttpResponse<void>> {
        return this.httpClient.delete<void>(
            `${this.resourceURL}/courses/${courseId}/tutorial-groups-configuration/${tutorialGroupConfigurationId}/tutorial-free-periods/${tutorialGroupFreePeriodId}`,
            { observe: 'response' },
        );
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
                startDate: toISO8601DateTimeString(tutorialGroupFreePeriodDTO.startDate),
                endDate: toISO8601DateTimeString(tutorialGroupFreePeriodDTO.endDate),
            });
        } else {
            return tutorialGroupFreePeriodDTO;
        }
    }
}
