import { Injectable } from '@angular/core';
import * as moment from 'moment';

@Injectable({ providedIn: 'root' })
export class ArtemisServerDateService {
    // offset of the last synchronization in ms
    private offset: number;

    /**
     * sets the current server date as unix
     *
     * @param {string} date
     */
    setServerDate(date: string): void {
        const serverDate = moment(date);
        const clientDate = moment(new Date());
        this.offset = serverDate.diff(clientDate, 'ms');
    }

    /**
     * returns the calculated current server date as moment
     */
    now(): moment.Moment {
        const clientDate = moment(new Date());
        // adjust with previously calculated offset
        return clientDate.add(this.offset, 'ms');
    }
}
