import { Injectable } from '@angular/core';
import dayjs from 'dayjs/esm';
import { HttpClient } from '@angular/common/http';

export interface ServerDateService {
    readonly http: HttpClient;
    readonly resourceUrl: string;
    // offsets of the last synchronizations in ms (max. 5)
    readonly recentOffsets: Array<number>;
    // client (!) dates of the last synchronizations (max. 5)
    readonly recentClientDates: Array<dayjs.Dayjs>;
    updateTime: () => void;
    setServerDate: (date: string) => void;
    now: () => dayjs.Dayjs;
}

@Injectable({ providedIn: 'root' })
export class ArtemisServerDateService implements ServerDateService {
    resourceUrl: string;
    recentOffsets: number[];
    recentClientDates: dayjs.Dayjs[];
    http: HttpClient;

    constructor(http: HttpClient) {
        this.http = http;
        this.resourceUrl = SERVER_API_URL + 'api/public/time';
        this.recentOffsets = new Array<number>();
        this.recentClientDates = new Array<dayjs.Dayjs>();
    }

    /**
     * get a new server date if necessary
     */
    updateTime(): void {
        let shouldSync = false;
        const now = dayjs(new Date());
        if (this.recentClientDates.length > 4) {
            // only if some recent client dates (i.e. recent syncs) are older than 60s
            shouldSync = this.recentClientDates.some((recentClientDate) => now.diff(recentClientDate, 's') > 60);
        } else {
            // definitely sync if we do not have 5 elements yet
            shouldSync = true;
        }
        // TODO: one additional optimization could be to take the duration for request -> response into account here
        if (shouldSync) {
            // get new server date
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
        const serverDate = dayjs(date);
        const clientDate = dayjs();
        // save the most recent client date
        this.recentClientDates.push(clientDate);
        // calculate offset
        const offset = serverDate.diff(clientDate, 'ms');
        // save the most recent offset
        this.recentOffsets.push(offset);
        // remove oldest offset and client date if more than 5
        if (this.recentOffsets.length > 5) {
            this.recentOffsets.shift();
            this.recentClientDates.shift();
        }
        // This would be faster and more reliable than using an interceptor with arbitrary REST calls
    }

    /**
     * returns the calculated current server date as dayjs
     */
    now(): dayjs.Dayjs {
        const clientDate = dayjs();
        // return the client date if there are no offsets (e.g. when offline or before any api call was made)
        if (this.recentOffsets.length === 0) {
            return clientDate;
        }
        // take first offset if there are less than 5
        let offset = this.recentOffsets[0];
        // remove noise from offset if there are 5
        if (this.recentOffsets.length === 5) {
            // work on copy of array
            const offsetsCopy = [...this.recentOffsets];
            const offsetsSorted = offsetsCopy.sort((a, b) => b - a);
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
