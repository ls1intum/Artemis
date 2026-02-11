import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { convertDateFromServer, toISO8601DateString } from 'app/shared/util/date.utils';
import { map } from 'rxjs/operators';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { TutorialGroupConfigurationDTO } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration-dto.model';

type EntityResponseType = HttpResponse<TutorialGroupsConfiguration>;
type DtoResponseType = HttpResponse<TutorialGroupConfigurationDTO>;

@Injectable({ providedIn: 'root' })
export class TutorialGroupsConfigurationService {
    private httpClient = inject(HttpClient);

    private resourceURL = 'api/tutorialgroup';

    getOneOfCourse(courseId: number) {
        return this.httpClient
            .get<TutorialGroupsConfiguration>(`${this.resourceURL}/courses/${courseId}/tutorial-groups-configuration`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertEntityResponseDatesFromServer(res)));
    }

    create(tutorialGroupsConfigurationDto: TutorialGroupConfigurationDTO, courseId: number, period: Date[]): Observable<DtoResponseType> {
        const copy = this.convertTutorialGroupsConfigurationDatesFromClient(tutorialGroupsConfigurationDto, period);
        return this.httpClient
            .post<TutorialGroupConfigurationDTO>(`${this.resourceURL}/courses/${courseId}/tutorial-groups-configuration`, copy, { observe: 'response' })
            .pipe(map((res: DtoResponseType) => this.convertDtoResponseDatesFromServer(res)));
    }

    update(courseId: number, tutorialGroupConfigurationId: number, tutorialGroupsConfigurationDto: TutorialGroupConfigurationDTO, period: Date[]): Observable<DtoResponseType> {
        const copy = this.convertTutorialGroupsConfigurationDatesFromClient(tutorialGroupsConfigurationDto, period);
        return this.httpClient
            .put<TutorialGroupConfigurationDTO>(`${this.resourceURL}/courses/${courseId}/tutorial-groups-configuration/${tutorialGroupConfigurationId}`, copy, {
                observe: 'response',
            })
            .pipe(map((res: DtoResponseType) => this.convertDtoResponseDatesFromServer(res)));
    }

    convertTutorialGroupsConfigurationDatesFromServer(tutorialGroupsConfiguration: TutorialGroupsConfiguration): TutorialGroupsConfiguration {
        tutorialGroupsConfiguration.tutorialPeriodStartInclusive = convertDateFromServer(tutorialGroupsConfiguration.tutorialPeriodStartInclusive);
        tutorialGroupsConfiguration.tutorialPeriodEndInclusive = convertDateFromServer(tutorialGroupsConfiguration.tutorialPeriodEndInclusive);
        if (tutorialGroupsConfiguration.tutorialGroupFreePeriods) {
            tutorialGroupsConfiguration.tutorialGroupFreePeriods.forEach((tutorialGroupFreePeriod) => {
                tutorialGroupFreePeriod.start = convertDateFromServer(tutorialGroupFreePeriod.start);
                tutorialGroupFreePeriod.end = convertDateFromServer(tutorialGroupFreePeriod.end);
            });
        }
        return tutorialGroupsConfiguration;
    }

    private convertEntityResponseDatesFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            this.convertTutorialGroupsConfigurationDatesFromServer(res.body);
        }
        return res;
    }

    private convertDtoResponseDatesFromServer(res: DtoResponseType): DtoResponseType {
        if (res.body) {
            res.body.tutorialPeriodStartInclusive = convertDateFromServer(res.body.tutorialPeriodStartInclusive);
            res.body.tutorialPeriodEndInclusive = convertDateFromServer(res.body.tutorialPeriodEndInclusive);
        }
        return res;
    }

    private convertTutorialGroupsConfigurationDatesFromClient(tutorialGroupsConfigurationDto: TutorialGroupConfigurationDTO, period: Date[]): TutorialGroupConfigurationDTO {
        return Object.assign({}, tutorialGroupsConfigurationDto, {
            tutorialPeriodStartInclusive: toISO8601DateString(period[0]),
            tutorialPeriodEndInclusive: toISO8601DateString(period[1]),
        });
    }
}
