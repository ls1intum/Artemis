import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { convertDateFromServer, toISO8601DateString } from 'app/shared/util/date.utils';
import { map } from 'rxjs/operators';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';

type EntityResponseType = HttpResponse<TutorialGroupsConfiguration>;

/**
 * DTO for creating and updating tutorial groups configuration.
 */
export interface TutorialGroupsConfigurationDTO {
    id?: number;
    tutorialPeriodStartInclusive: string;
    tutorialPeriodEndInclusive: string;
    useTutorialGroupChannels: boolean;
    usePublicTutorialGroupChannels: boolean;
}

@Injectable({ providedIn: 'root' })
export class TutorialGroupsConfigurationService {
    private httpClient = inject(HttpClient);

    private resourceURL = 'api/tutorialgroup';

    getOneOfCourse(courseId: number) {
        return this.httpClient
            .get<TutorialGroupsConfiguration>(`${this.resourceURL}/courses/${courseId}/tutorial-groups-configuration`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupsConfigurationResponseDatesFromServer(res)));
    }
    create(tutorialGroupsConfiguration: TutorialGroupsConfiguration, courseId: number, period: Date[]): Observable<EntityResponseType> {
        const dto = this.toDTO(tutorialGroupsConfiguration, period);
        return this.httpClient
            .post<TutorialGroupsConfiguration>(`${this.resourceURL}/courses/${courseId}/tutorial-groups-configuration`, dto, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupsConfigurationResponseDatesFromServer(res)));
    }

    update(courseId: number, tutorialGroupConfigurationId: number, tutorialGroupsConfiguration: TutorialGroupsConfiguration, period: Date[]): Observable<EntityResponseType> {
        const dto = this.toDTO(tutorialGroupsConfiguration, period, tutorialGroupConfigurationId);
        return this.httpClient
            .put<TutorialGroupsConfiguration>(`${this.resourceURL}/courses/${courseId}/tutorial-groups-configuration/${tutorialGroupConfigurationId}`, dto, {
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupsConfigurationResponseDatesFromServer(res)));
    }

    /**
     * Converts a TutorialGroupsConfiguration to a DTO for sending to the server.
     */
    private toDTO(config: TutorialGroupsConfiguration, period: Date[], id?: number): TutorialGroupsConfigurationDTO {
        return {
            id: id,
            tutorialPeriodStartInclusive: toISO8601DateString(period[0])!,
            tutorialPeriodEndInclusive: toISO8601DateString(period[1])!,
            useTutorialGroupChannels: config.useTutorialGroupChannels ?? false,
            usePublicTutorialGroupChannels: config.usePublicTutorialGroupChannels ?? false,
        };
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

    private convertTutorialGroupsConfigurationResponseDatesFromServer(res: HttpResponse<TutorialGroupsConfiguration>): HttpResponse<TutorialGroupsConfiguration> {
        if (res.body) {
            res.body.tutorialPeriodStartInclusive = convertDateFromServer(res.body!.tutorialPeriodStartInclusive);
            res.body!.tutorialPeriodEndInclusive = convertDateFromServer(res.body!.tutorialPeriodEndInclusive);
            if (res.body!.tutorialGroupFreePeriods) {
                res.body!.tutorialGroupFreePeriods.forEach((tutorialGroupFreePeriod) => {
                    tutorialGroupFreePeriod.start = convertDateFromServer(tutorialGroupFreePeriod.start);
                    tutorialGroupFreePeriod.end = convertDateFromServer(tutorialGroupFreePeriod.end);
                });
            }
        }

        return res;
    }
}
