import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { Observable } from 'rxjs';
import { convertDateFromServer } from 'app/utils/date.utils';
import { map } from 'rxjs/operators';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';

type EntityResponseType = HttpResponse<TutorialGroupsConfiguration>;

@Injectable({ providedIn: 'root' })
export class TutorialGroupsConfigurationService {
    private resourceURL = SERVER_API_URL + 'api';

    constructor(private httpClient: HttpClient) {}

    getOfCourse(tutorialGroupsConfigurationId: number, courseId: number) {
        return this.httpClient
            .get<TutorialGroupsConfiguration>(`${this.resourceURL}/courses/${courseId}/tutorial-groups-configuration/${tutorialGroupsConfigurationId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupsConfigurationResponseDatesFromServer(res)));
    }
    create(tutorialGroupsConfiguration: TutorialGroupsConfiguration, courseId: number): Observable<EntityResponseType> {
        const copy = this.convertTutorialGroupsConfigurationDatesFromClient(tutorialGroupsConfiguration);
        return this.httpClient
            .post<TutorialGroupsConfiguration>(`${this.resourceURL}/courses/${courseId}/tutorial-groups-configuration`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupsConfigurationResponseDatesFromServer(res)));
    }

    update(tutorialGroupsConfiguration: TutorialGroupsConfiguration, courseId: number): Observable<EntityResponseType> {
        const copy = this.convertTutorialGroupsConfigurationDatesFromClient(tutorialGroupsConfiguration);
        return this.httpClient
            .put<TutorialGroup>(`${this.resourceURL}/courses/${courseId}/tutorial-groups-configuration/${tutorialGroupsConfiguration.id}`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupsConfigurationResponseDatesFromServer(res)));
    }

    convertTutorialGroupsConfigurationDatesFromServer(tutorialGroupsConfiguration: TutorialGroupsConfiguration): TutorialGroupsConfiguration {
        tutorialGroupsConfiguration.tutorialPeriodStartInclusive = convertDateFromServer(tutorialGroupsConfiguration.tutorialPeriodStartInclusive);
        tutorialGroupsConfiguration.tutorialPeriodEndInclusive = convertDateFromServer(tutorialGroupsConfiguration.tutorialPeriodEndInclusive);
        return tutorialGroupsConfiguration;
    }

    private convertTutorialGroupsConfigurationResponseDatesFromServer(res: HttpResponse<TutorialGroupsConfiguration>): HttpResponse<TutorialGroupsConfiguration> {
        res.body!.tutorialPeriodStartInclusive = convertDateFromServer(res.body!.tutorialPeriodStartInclusive);
        res.body!.tutorialPeriodEndInclusive = convertDateFromServer(res.body!.tutorialPeriodEndInclusive);
        return res;
    }

    private convertTutorialGroupsConfigurationDatesFromClient(tutorialGroupsConfiguration: TutorialGroupsConfiguration): TutorialGroupsConfiguration {
        return Object.assign({}, tutorialGroupsConfiguration, {
            tutorialPeriodStartInclusive: tutorialGroupsConfiguration.tutorialPeriodStartInclusive?.format('YYYY-MM-DD'),
            tutorialPeriodEndInclusive: tutorialGroupsConfiguration.tutorialPeriodEndInclusive?.format('YYYY-MM-DD'),
        });
    }
}
