import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { convertDateFromServer, toISO8601DateString } from 'app/utils/date.utils';
import { map } from 'rxjs/operators';
import { TutorialGroupFreeDay } from 'app/entities/tutorial-group/tutorial-group-free-day.model';

type EntityResponseType = HttpResponse<TutorialGroupFreeDay>;

export class TutorialGroupFreeDayDTO {
    public date?: Date;
    public reason?: string;
}

@Injectable({ providedIn: 'root' })
export class TutorialGroupFreeDayService {
    private resourceURL = 'api';

    constructor(private httpClient: HttpClient) {}

    getOneOfConfiguration(courseId: number, tutorialGroupsConfigurationId: number, tutorialGroupFreeDayId: number): Observable<EntityResponseType> {
        return this.httpClient
            .get<TutorialGroupFreeDay>(
                `${this.resourceURL}/courses/${courseId}/tutorial-groups-configuration/${tutorialGroupsConfigurationId}/tutorial-free-days/${tutorialGroupFreeDayId}`,
                { observe: 'response' },
            )
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupFreeDayResponseDatesFromServer(res)));
    }

    create(courseId: number, tutorialGroupConfigurationId: number, tutorialGroupFreeDayDTO: TutorialGroupFreeDayDTO): Observable<EntityResponseType> {
        const copy = this.convertTutorialGroupFreeDayDatesFromClient(tutorialGroupFreeDayDTO);
        return this.httpClient
            .post<TutorialGroupFreeDay>(`${this.resourceURL}/courses/${courseId}/tutorial-groups-configuration/${tutorialGroupConfigurationId}/tutorial-free-days`, copy, {
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupFreeDayResponseDatesFromServer(res)));
    }

    update(
        courseId: number,
        tutorialGroupConfigurationId: number,
        tutorialGroupFreeDayId: number,
        tutorialGroupFreeDayDTO: TutorialGroupFreeDayDTO,
    ): Observable<EntityResponseType> {
        const copy = this.convertTutorialGroupFreeDayDatesFromClient(tutorialGroupFreeDayDTO);
        return this.httpClient
            .put<TutorialGroupFreeDay>(
                `${this.resourceURL}/courses/${courseId}/tutorial-groups-configuration/${tutorialGroupConfigurationId}/tutorial-free-days/${tutorialGroupFreeDayId}`,
                copy,
                {
                    observe: 'response',
                },
            )
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupFreeDayResponseDatesFromServer(res)));
    }

    delete(courseId: number, tutorialGroupConfigurationId: number, tutorialGroupFreeDayId: number): Observable<HttpResponse<void>> {
        return this.httpClient.delete<void>(
            `${this.resourceURL}/courses/${courseId}/tutorial-groups-configuration/${tutorialGroupConfigurationId}/tutorial-free-days/${tutorialGroupFreeDayId}`,
            { observe: 'response' },
        );
    }

    convertTutorialGroupFreeDayDatesFromServer(tutorialGroupFreeDay: TutorialGroupFreeDay): TutorialGroupFreeDay {
        tutorialGroupFreeDay.start = convertDateFromServer(tutorialGroupFreeDay.start);
        tutorialGroupFreeDay.end = convertDateFromServer(tutorialGroupFreeDay.end);
        return tutorialGroupFreeDay;
    }

    private convertTutorialGroupFreeDayResponseDatesFromServer(res: HttpResponse<TutorialGroupFreeDay>): HttpResponse<TutorialGroupFreeDay> {
        if (res.body) {
            this.convertTutorialGroupFreeDayDatesFromServer(res.body);
        }
        return res;
    }

    private convertTutorialGroupFreeDayDatesFromClient(tutorialGroupFreeDayDTO: TutorialGroupFreeDayDTO): TutorialGroupFreeDayDTO {
        if (tutorialGroupFreeDayDTO) {
            return Object.assign({}, tutorialGroupFreeDayDTO, {
                date: toISO8601DateString(tutorialGroupFreeDayDTO.date),
            });
        } else {
            return tutorialGroupFreeDayDTO;
        }
    }
}
