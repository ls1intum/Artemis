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
        // TODO: to improve the offset calculation, we should take the diff between request_date and response_date and divide it by 2
        // TODO: to eliminate noise, we should store the 5 last offsets, remove the smallest and highest value and take the average of the remaining 3
        // TODO: it might make sense to invoke one very fast explicit REST/websocket calls (e.g. "/time") without access control and database access.
        // This would be faster and more reliable than using an interceptor with arbitrary REST calls
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
