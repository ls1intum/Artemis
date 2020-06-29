import { Injectable } from '@angular/core';
import * as moment from 'moment';
import { HttpClient } from '@angular/common/http';
import { SERVER_API_URL } from 'app/app.constants';

@Injectable({ providedIn: 'root' })
export class ArtemisServerDateService {
    private resourceUrl = SERVER_API_URL + 'time';

    // offsets of the last synchronizations in ms (max. 5)
    private recentOffsets = new Array<number>();
    private recentClientDates = new Array<moment.Moment>();

    constructor(private http: HttpClient) {}

    updateTime(): void {
        let shouldSync = false;
        const now = moment(new Date());
        if (this.recentClientDates.length > 4) {
            this.recentClientDates.forEach((recentClientDate) => {
                // only if all recent client dates (i.e. recent syncs are older than 60s)
                if (now.diff(recentClientDate, 's') > 60) {
                    shouldSync = true;
                }
            });
        } else {
            // definitly sync if we do not have 5 elements yet
            shouldSync = true;
        }
        // TODO: one additional optimization could be to take the duration for request -> response into account here
        if (shouldSync) {
            this.http.get<string>(this.resourceUrl).subscribe((serverDate) => {
                this.setServerDate(serverDate);
            });
        }
    }

    /**
     * adds the latest offset
     *
     * @param {string} date
     */
    setServerDate(date: string): void {
        const serverDate = moment(date);
        const clientDate = moment();
        this.recentClientDates.push(clientDate);
        const offset = serverDate.diff(clientDate, 'ms');
        this.recentOffsets.push(offset);
        // remove oldest offset if more than 5
        if (this.recentOffsets.length > 5) {
            this.recentOffsets.shift();
            this.recentClientDates.shift();
        }
        // This would be faster and more reliable than using an interceptor with arbitrary REST calls
    }

    /**
     * returns the calculated current server date as moment
     */
    now(): moment.Moment {
        const clientDate = moment();
        if (this.recentOffsets.length === 0) {
            return clientDate;
        }
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
