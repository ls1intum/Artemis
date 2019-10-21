/**
 * Angular bootstrap Date adapter
 */
import { Injectable } from '@angular/core';
import { NgbDateAdapter, NgbDateStruct } from '@ng-bootstrap/ng-bootstrap';
import * as moment from 'moment';
import { Moment } from 'moment';

@Injectable({ providedIn: 'root' })
export class NgbDateMomentAdapter extends NgbDateAdapter<Moment> {
    fromModel(date: Moment): NgbDateStruct {
        if (date != null && moment.isMoment(date) && date.isValid()) {
            return { year: date.year(), month: date.month() + 1, day: date.date() };
        }
        return { year: moment().year(), month: moment().month() + 1, day: moment().date() };
    }

    toModel(date: NgbDateStruct): Moment {
        return date ? moment(date.year + '-' + date.month + '-' + date.day, 'YYYY-MM-DD') : moment();
    }
}
