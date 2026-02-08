import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { convertDateFromServer } from 'app/shared/util/date.utils';
import { map } from 'rxjs/operators';
import { TutorialGroupFreePeriodApiService } from 'app/openapi/api/tutorialGroupFreePeriodApi.service';
import { TutorialGroupFreePeriodDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-free-period-dto.model';

type EntityResponseType = HttpResponse<TutorialGroupFreePeriodDTO>;

@Injectable({ providedIn: 'root' })
export class TutorialGroupFreePeriodService {
    private httpClient = inject(HttpClient);
    private tutorialGroupFreePeriodApiService = inject(TutorialGroupFreePeriodApiService);

    private resourceURL = 'api/tutorialgroup';

    getOneOfConfiguration(courseId: number, tutorialGroupsConfigurationId: number, tutorialGroupFreePeriodId: number): Observable<EntityResponseType> {
        return this.httpClient
            .get<TutorialGroupFreePeriodDTO>(
                `${this.resourceURL}/courses/${courseId}/tutorial-groups-configuration/${tutorialGroupsConfigurationId}/tutorial-free-periods/${tutorialGroupFreePeriodId}`,
                { observe: 'response' },
            )
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupFreePeriodResponseDatesFromServer(res)));
    }

    create(courseId: number, tutorialGroupConfigurationId: number, tutorialGroupFreePeriodDTO: TutorialGroupFreePeriodDTO): Observable<EntityResponseType> {
        return this.httpClient
            .post<TutorialGroupFreePeriodDTO>(
                `${this.resourceURL}/courses/${courseId}/tutorial-groups-configuration/${tutorialGroupConfigurationId}/tutorial-free-periods`,
                tutorialGroupFreePeriodDTO,
                {
                    observe: 'response',
                },
            )
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupFreePeriodResponseDatesFromServer(res)));
    }

    update(
        courseId: number,
        tutorialGroupConfigurationId: number,
        tutorialGroupFreePeriodId: number,
        tutorialGroupFreePeriodDTO: TutorialGroupFreePeriodDTO,
    ): Observable<EntityResponseType> {
        return this.httpClient
            .put<TutorialGroupFreePeriodDTO>(
                `${this.resourceURL}/courses/${courseId}/tutorial-groups-configuration/${tutorialGroupConfigurationId}/tutorial-free-periods/${tutorialGroupFreePeriodId}`,
                tutorialGroupFreePeriodDTO,
                {
                    observe: 'response',
                },
            )
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupFreePeriodResponseDatesFromServer(res)));
    }

    delete(courseId: number, tutorialGroupConfigurationId: number, tutorialGroupFreePeriodId: number): Observable<HttpResponse<void>> {
        return this.tutorialGroupFreePeriodApiService.delete(courseId, tutorialGroupConfigurationId, tutorialGroupFreePeriodId, 'response');
    }

    convertTutorialGroupFreePeriodDatesFromServer(tutorialGroupFreePeriodDto: TutorialGroupFreePeriodDTO): TutorialGroupFreePeriodDTO {
        tutorialGroupFreePeriodDto.start = convertDateFromServer(tutorialGroupFreePeriodDto.start);
        tutorialGroupFreePeriodDto.end = convertDateFromServer(tutorialGroupFreePeriodDto.end);
        return tutorialGroupFreePeriodDto;
    }

    private convertTutorialGroupFreePeriodResponseDatesFromServer(res: HttpResponse<TutorialGroupFreePeriodDTO>): HttpResponse<TutorialGroupFreePeriodDTO> {
        if (res.body) {
            this.convertTutorialGroupFreePeriodDatesFromServer(res.body);
        }
        return res;
    }
}
