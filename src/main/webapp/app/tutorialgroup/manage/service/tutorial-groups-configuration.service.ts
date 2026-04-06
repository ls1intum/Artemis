import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { toISO8601DateString } from 'app/shared/util/date.utils';
import { TutorialGroupConfigurationDTO } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration-dto.model';

type DtoResponseType = HttpResponse<TutorialGroupConfigurationDTO>;

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
        return this.httpClient.get<TutorialGroupConfigurationDTO>(`${this.resourceURL}/courses/${courseId}/tutorial-groups-configuration`, { observe: 'response' });
    }

    create(tutorialGroupsConfigurationDto: TutorialGroupConfigurationDTO, courseId: number, period: Date[]): Observable<DtoResponseType> {
        const copy = this.convertTutorialGroupsConfigurationDatesFromClient(tutorialGroupsConfigurationDto, period);
        return this.httpClient.post<TutorialGroupConfigurationDTO>(`${this.resourceURL}/courses/${courseId}/tutorial-groups-configuration`, copy, { observe: 'response' });
    }

    update(courseId: number, tutorialGroupConfigurationId: number, tutorialGroupsConfigurationDto: TutorialGroupConfigurationDTO, period: Date[]): Observable<DtoResponseType> {
        const copy = this.convertTutorialGroupsConfigurationDatesFromClient(tutorialGroupsConfigurationDto, period);
        return this.httpClient.put<TutorialGroupConfigurationDTO>(`${this.resourceURL}/courses/${courseId}/tutorial-groups-configuration/${tutorialGroupConfigurationId}`, copy, {
            observe: 'response',
        });
    }

    private convertTutorialGroupsConfigurationDatesFromClient(tutorialGroupsConfigurationDto: TutorialGroupConfigurationDTO, period: Date[]): TutorialGroupConfigurationDTO {
        return Object.assign({}, tutorialGroupsConfigurationDto, {
            tutorialPeriodStartInclusive: toISO8601DateString(period[0]),
            tutorialPeriodEndInclusive: toISO8601DateString(period[1]),
            tutorialGroupFreePeriods: tutorialGroupsConfigurationDto.tutorialGroupFreePeriods ?? [],
        });
    }
}
