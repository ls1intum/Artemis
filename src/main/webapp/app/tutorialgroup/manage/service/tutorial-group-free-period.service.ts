import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import dayjs from 'dayjs/esm';
import { TutorialGroupFreePeriodApi } from 'app/openapi/api/tutorial-group-free-period-api';
import { TutorialGroupFreePeriod } from 'app/openapi/model/tutorial-group-free-period';
import { TutorialGroupFreePeriodRequest } from 'app/openapi/model/tutorial-group-free-period-request';
import { TutorialGroupFreePeriodSessionCount } from 'app/openapi/model/tutorial-group-free-period-session-count';
import { TutorialGroupSessionCount } from 'app/openapi/model/tutorial-group-session-count';

/** The server reads and writes the day of a holiday as a plain calendar date, without a zone. */
const SERVER_DATE_FORMAT = 'YYYY-MM-DD';

/**
 * How the server takes the bounds of a free period: a wall clock with no offset, which it then reads in the course's
 * time zone. Sending an instant instead would have the browser's zone decide the wall clock, and the server would
 * reinterpret those digits in the course's - moving the holiday whenever the two zones differ.
 */
const SERVER_DATE_TIME_FORMAT = 'YYYY-MM-DDTHH:mm:ss';

/** A holiday as the dialog submits it. */
export interface TutorialGroupFreePeriodDTO {
    /** Carried as Dayjs rather than Date so the value keeps the zone it was chosen in until it is written out. */
    startDate: dayjs.Dayjs;
    endDate: dayjs.Dayjs;
    reason?: string;
}

@Service()
export class TutorialGroupFreePeriodService {
    private readonly api = inject(TutorialGroupFreePeriodApi);

    create(courseId: number, tutorialGroupConfigurationId: number, tutorialGroupFreePeriodDTO: TutorialGroupFreePeriodDTO): Observable<TutorialGroupFreePeriod> {
        return this.api.create(courseId, tutorialGroupConfigurationId, toRequest(tutorialGroupFreePeriodDTO));
    }

    update(
        courseId: number,
        tutorialGroupConfigurationId: number,
        tutorialGroupFreePeriodId: number,
        tutorialGroupFreePeriodDTO: TutorialGroupFreePeriodDTO,
    ): Observable<TutorialGroupFreePeriod> {
        return this.api.update(courseId, tutorialGroupConfigurationId, tutorialGroupFreePeriodId, toRequest(tutorialGroupFreePeriodDTO));
    }

    delete(courseId: number, tutorialGroupConfigurationId: number, tutorialGroupFreePeriodId: number): Observable<void> {
        return this.api.delete(courseId, tutorialGroupConfigurationId, tutorialGroupFreePeriodId);
    }

    /**
     * Loads how many tutorial group sessions the course holds on each day of the given span.
     *
     * The holidays page asks for a whole month at a time, because it labels every day of the grid as well as every
     * holiday in the list. Days without a session are omitted by the server rather than returned as zero.
     */
    getSessionCounts(courseId: number, from: dayjs.Dayjs, to: dayjs.Dayjs): Observable<TutorialGroupSessionCount[]> {
        return this.api.getSessionCounts(courseId, from.format(SERVER_DATE_FORMAT), to.format(SERVER_DATE_FORMAT));
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
        return this.api.getOverlappingSessionCount(courseId, from.format(SERVER_DATE_TIME_FORMAT), to.format(SERVER_DATE_TIME_FORMAT), editedFreePeriodId);
    }

    /** Counts the sessions every free period of the course covers, in one request rather than one per holiday. */
    getSessionCountsPerFreePeriod(courseId: number): Observable<TutorialGroupFreePeriodSessionCount[]> {
        return this.api.getSessionCountsPerFreePeriod(courseId);
    }
}

/**
 * Writes the bounds as the wall clock of the zone they were chosen in.
 *
 * Dayjs formats in its own zone, so a value read in the course's zone stays that way. Going through a Date first
 * would hand the formatting to the browser's zone and shift the holiday for anyone not sitting in the course's.
 */
function toRequest(tutorialGroupFreePeriodDTO: TutorialGroupFreePeriodDTO): TutorialGroupFreePeriodRequest {
    return {
        startDate: tutorialGroupFreePeriodDTO.startDate.format(SERVER_DATE_TIME_FORMAT),
        endDate: tutorialGroupFreePeriodDTO.endDate.format(SERVER_DATE_TIME_FORMAT),
        reason: tutorialGroupFreePeriodDTO.reason,
    };
}
