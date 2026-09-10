import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import dayjs from 'dayjs/esm';
import { toISO8601DateTimeString } from 'app/foundation/util/date.utils';
import { map } from 'rxjs/operators';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { convertTutorialGroupFreePeriodDatesFromServer } from 'app/tutorialgroup/shared/util/convertTutorialGroupEntityDates';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

type EntityResponseType = HttpResponse<TutorialGroupFreePeriod>;

/** The server reads and writes the day of a holiday as a plain calendar date, without a zone. */
const SERVER_DATE_FORMAT = 'YYYY-MM-DD';

export class TutorialGroupFreePeriodDTO {
    public startDate?: Date;
    public endDate?: Date;
    public reason?: string;
}

/** How many sessions a course holds on one day, as counted in the time zone of the tutorial groups configuration. */
export interface TutorialGroupSessionCount {
    /** `YYYY-MM-DD`, so it can be used as a map key without a zone conversion on the client. */
    date: string;
    count: number;
}

/** One public holiday on offer for import. */
export interface PublicHoliday {
    date: string;
    name: string;
    alreadyExists: boolean;
}

export interface PublicHolidaySuggestions {
    /** False while Artemis has no source of public holidays configured, as opposed to a span that simply has none. */
    configured: boolean;
    holidays?: PublicHoliday[];
}

@Injectable({ providedIn: 'root' })
export class TutorialGroupFreePeriodService {
    private httpClient = inject(HttpClient);

    private resourceURL = 'api/tutorialgroup';

    getOneOfConfiguration(courseId: number, tutorialGroupsConfigurationId: number, tutorialGroupFreePeriodId: number): Observable<EntityResponseType> {
        return this.httpClient
            .get<TutorialGroupFreePeriod>(
                `${this.resourceURL}/courses/${courseId}/tutorial-groups-configurations/${tutorialGroupsConfigurationId}/tutorial-free-periods/${tutorialGroupFreePeriodId}`,
                { observe: 'response' },
            )
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupFreePeriodResponseDatesFromServer(res)));
    }

    create(courseId: number, tutorialGroupConfigurationId: number, tutorialGroupFreePeriodDTO: TutorialGroupFreePeriodDTO): Observable<EntityResponseType> {
        const copy = this.convertTutorialGroupFreePeriodDatesFromClient(tutorialGroupFreePeriodDTO);
        return this.httpClient
            .post<TutorialGroupFreePeriod>(`${this.resourceURL}/courses/${courseId}/tutorial-groups-configurations/${tutorialGroupConfigurationId}/tutorial-free-periods`, copy, {
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
                `${this.resourceURL}/courses/${courseId}/tutorial-groups-configurations/${tutorialGroupConfigurationId}/tutorial-free-periods/${tutorialGroupFreePeriodId}`,
                copy,
                {
                    observe: 'response',
                },
            )
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupFreePeriodResponseDatesFromServer(res)));
    }

    delete(courseId: number, tutorialGroupConfigurationId: number, tutorialGroupFreePeriodId: number): Observable<HttpResponse<void>> {
        return this.httpClient.delete<void>(
            `${this.resourceURL}/courses/${courseId}/tutorial-groups-configurations/${tutorialGroupConfigurationId}/tutorial-free-periods/${tutorialGroupFreePeriodId}`,
            { observe: 'response' },
        );
    }

    /**
     * Loads how many tutorial group sessions the course holds on each day of the given span.
     *
     * The holidays page asks for a whole month at a time, because it labels every day of the grid as well as every
     * holiday in the list. Days without a session are omitted by the server rather than returned as zero.
     */
    getSessionCounts(courseId: number, from: dayjs.Dayjs, to: dayjs.Dayjs): Observable<TutorialGroupSessionCount[]> {
        const params = new HttpParams().set('from', from.format(SERVER_DATE_FORMAT)).set('to', to.format(SERVER_DATE_FORMAT));
        return this.httpClient.get<TutorialGroupSessionCount[]>(`${this.resourceURL}/courses/${courseId}/tutorial-free-periods/session-counts`, { params });
    }

    /**
     * Loads the public holidays that can be imported into the course.
     *
     * `configured` is false while Artemis has no source of public holidays, which the caller explains rather than
     * rendering as "no holidays found".
     */
    getPublicHolidays(courseId: number, from: dayjs.Dayjs, to: dayjs.Dayjs): Observable<PublicHolidaySuggestions> {
        const params = new HttpParams().set('from', from.format(SERVER_DATE_FORMAT)).set('to', to.format(SERVER_DATE_FORMAT));
        return this.httpClient.get<PublicHolidaySuggestions>(`${this.resourceURL}/courses/${courseId}/tutorial-free-periods/public-holidays`, { params });
    }

    private convertTutorialGroupFreePeriodResponseDatesFromServer(res: HttpResponse<TutorialGroupFreePeriod>): HttpResponse<TutorialGroupFreePeriod> {
        if (res.body) {
            convertTutorialGroupFreePeriodDatesFromServer(res.body);
        }
        return res;
    }

    private convertTutorialGroupFreePeriodDatesFromClient(tutorialGroupFreePeriodDTO: TutorialGroupFreePeriodDTO): TutorialGroupFreePeriodDTO {
        if (tutorialGroupFreePeriodDTO) {
            return cloneWith(tutorialGroupFreePeriodDTO, {
                startDate: toISO8601DateTimeString(tutorialGroupFreePeriodDTO.startDate),
                endDate: toISO8601DateTimeString(tutorialGroupFreePeriodDTO.endDate),
            });
        } else {
            return tutorialGroupFreePeriodDTO;
        }
    }
}
