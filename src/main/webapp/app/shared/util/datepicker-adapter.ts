/**
 * Angular bootstrap Date adapter
 */
import { Injectable } from '@angular/core';
import { NgbDateAdapter, NgbDateStruct } from '@ng-bootstrap/ng-bootstrap';
import * as moment from 'moment';
import { Moment } from 'moment';

@Injectable({ providedIn: 'root' })
export class NgbDateMomentAdapter extends NgbDateAdapter<Moment> {
    /**
     * Converts the date from a Moment type to an NgbDateStruct object
     * @param date The date as a Moment object
     */
    fromModel(date: Moment): NgbDateStruct {
        if (date != undefined && moment.isMoment(date) && date.isValid()) {
            return { year: date.year(), month: date.month() + 1, day: date.date() };
        }
        return { year: moment().year(), month: moment().month() + 1, day: moment().date() };
    }

    /**
     * Converts the date from an NgbDateStruct type to a Moment object
     * @param date The date as an NgbDateStruct object
     */
    toModel(date: NgbDateStruct): Moment {
        return date ? moment(date.year + '-' + date.month + '-' + date.day, 'YYYY-MM-DD') : moment();
    }
}
