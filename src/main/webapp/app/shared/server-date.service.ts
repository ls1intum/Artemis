import { Injectable } from '@angular/core';
import { interval } from 'rxjs/observable/interval';
import * as moment from 'moment';

@Injectable({ providedIn: 'root' })
export class ArtemisServerDateService {
    // date from the server saved in unix for easier calculation
    private serverDate: number;
    // interval firing every second to increment the server date
    private interval = interval(1000);

    constructor() {
        this.interval.subscribe(() => {
            // increment the server date by a second every second
            this.serverDate++;
        });
    }

    /**
     * sets the current server date as unix
     *
     * @param {string} date
     */
    setServerDate(date: string): void {
        this.serverDate = moment(date).unix();
    }

    /**
     * returns the calculated current server date as moment
     */
    now(): any {
        // return moment.unix(this.serverDate);
        // NOTE: quick fix which basically deactivates the calculation here until a better approach is implemented and tested
        return moment();
    }
}
