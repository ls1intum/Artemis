import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import dayjs from 'dayjs/esm';
import { map } from 'rxjs/operators';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { convertTutorialGroupFreePeriodDatesFromServer } from 'app/tutorialgroup/shared/util/convertTutorialGroupEntityDates';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

type EntityResponseType = HttpResponse<TutorialGroupFreePeriod>;

/** The server reads and writes the day of a holiday as a plain calendar date, without a zone. */
const SERVER_DATE_FORMAT = 'YYYY-MM-DD';

/**
 * How the server takes the bounds of a free period: a wall clock with no offset, which it then reads in the course's
 * time zone. Sending an instant instead would have the browser's zone decide the wall clock, and the server would
 * reinterpret those digits in the course's - moving the holiday whenever the two zones differ.
 */
const SERVER_DATE_TIME_FORMAT = 'YYYY-MM-DDTHH:mm:ss';

export class TutorialGroupFreePeriodDTO {
    /** Carried as Dayjs rather than Date so the value keeps the zone it was chosen in until it is written out. */
    public startDate?: dayjs.Dayjs;
    public endDate?: dayjs.Dayjs;
    public reason?: string;
}

/** How many sessions one free period covers, counted by overlap rather than by whole days. */
export interface TutorialGroupFreePeriodSessionCount {
    freePeriodId: number;
    count: number;
}

/** How many sessions a course holds on one day, as counted in the time zone of the tutorial groups configuration. */
export interface TutorialGroupSessionCount {
    /** `YYYY-MM-DD`, so it can be used as a map key without a zone conversion on the client. */
    date: string;
    count: number;
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
     * Counts the sessions saving a holiday over this span would cancel.
     *
     * Separate from the per-day counts the calendar is labelled with, because cancelling goes by overlap: a holiday
     * from 09:00 to 10:00 leaves that afternoon's sessions alone, and the day's total would say otherwise.
     *
     * `editedFreePeriodId` names the holiday being edited, whose own cancelled sessions it would release and take
     * again; without it, reopening a saved holiday unchanged would report that it cancels nothing.
     */
    getOverlappingSessionCount(courseId: number, from: dayjs.Dayjs, to: dayjs.Dayjs, editedFreePeriodId?: number): Observable<number> {
        let params = new HttpParams().set('from', from.format(SERVER_DATE_TIME_FORMAT)).set('to', to.format(SERVER_DATE_TIME_FORMAT));
        if (editedFreePeriodId !== undefined) {
            params = params.set('editedFreePeriodId', editedFreePeriodId);
        }
        return this.httpClient.get<number>(`${this.resourceURL}/courses/${courseId}/tutorial-free-periods/overlapping-session-count`, { params });
    }

    /** Counts the sessions every free period of the course covers, in one request rather than one per holiday. */
    getSessionCountsPerFreePeriod(courseId: number): Observable<TutorialGroupFreePeriodSessionCount[]> {
        return this.httpClient.get<TutorialGroupFreePeriodSessionCount[]>(`${this.resourceURL}/courses/${courseId}/tutorial-free-periods/session-counts-per-period`);
    }

    private convertTutorialGroupFreePeriodResponseDatesFromServer(res: HttpResponse<TutorialGroupFreePeriod>): HttpResponse<TutorialGroupFreePeriod> {
        if (res.body) {
            convertTutorialGroupFreePeriodDatesFromServer(res.body);
        }
        return res;
    }

    /**
     * Writes the bounds as the wall clock of the zone they were chosen in.
     *
     * Dayjs formats in its own zone, so a value read in the course's zone stays that way. Going through a Date first
     * would hand the formatting to the browser's zone and shift the holiday for anyone not sitting in the course's.
     */
    private convertTutorialGroupFreePeriodDatesFromClient(tutorialGroupFreePeriodDTO: TutorialGroupFreePeriodDTO): TutorialGroupFreePeriodDTO {
        if (tutorialGroupFreePeriodDTO) {
            return cloneWith(tutorialGroupFreePeriodDTO, {
                startDate: tutorialGroupFreePeriodDTO.startDate?.format(SERVER_DATE_TIME_FORMAT),
                endDate: tutorialGroupFreePeriodDTO.endDate?.format(SERVER_DATE_TIME_FORMAT),
            });
        } else {
            return tutorialGroupFreePeriodDTO;
        }
    }
}
