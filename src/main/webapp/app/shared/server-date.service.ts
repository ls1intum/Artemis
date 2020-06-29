import { Injectable } from '@angular/core';
import * as moment from 'moment';

@Injectable({ providedIn: 'root' })
export class ArtemisServerDateService {
    // offsets of the last synchronizations in ms (max. 5)
    private recentOffsets: number[];

    /**
     * adds the latest offset
     *
     * @param {string} date
     */
    setServerDate(date: string): void {
        const serverDate = moment(date);
        const clientDate = moment(new Date());
        const offset = serverDate.diff(clientDate, 'ms') / 2;
        this.recentOffsets.push(offset);
        // remove oldest offset if more than 5
        if (this.recentOffsets.length > 5) {
            this.recentOffsets.shift();
        }
        // TODO: it might make sense to invoke one very fast explicit REST/websocket calls (e.g. "/time") without access control and database access.
        // This would be faster and more reliable than using an interceptor with arbitrary REST calls
    }

    /**
     * returns the calculated current server date as moment
     */
    now(): moment.Moment {
        const clientDate = moment(new Date());
        // take first offset if there are less than 5
        let offset = this.recentOffsets[0];
        if (this.recentOffsets.length === 5) {
            const offsetsSorted = this.recentOffsets.sort((a, b) => b - a);
            // remove lowest
            offsetsSorted.shift();
            // remove highest
            offsetsSorted.pop();
            // calculate avg
            offset = offsetsSorted.reduce((a, b) => a + b) / offsetsSorted.length;
        }
        // adjust with previously calculated offset
        return clientDate.add(offset, 'ms');
    }
}
