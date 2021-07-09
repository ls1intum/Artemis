import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'artemisDurationFromSeconds' })
export class ArtemisDurationFromSecondsPipe implements PipeTransform {
    private readonly secondsInDay = 60 * 60 * 24;
    private readonly secondsInHour = 60 * 60;
    private readonly secondsInMinute = 60;

    /**
     * Convert seconds to a human-readable duration format "d day(s) hh:mm::ss".
     * The days and hours are left out if their value is zero
     * @param seconds {number}
     */
    transform(seconds: number): string {
        const days = Math.floor(seconds / this.secondsInDay);
        const hours = Math.floor((seconds % this.secondsInDay) / this.secondsInHour);
        const minutes = Math.floor((seconds % this.secondsInHour) / this.secondsInMinute);
        seconds = seconds % this.secondsInMinute;

        let timeString = this.transformDays(days);

        if (days > 0 || hours > 0) {
            timeString += this.addLeadingZero(hours) + ':';
        }

        timeString += this.addLeadingZero(minutes);
        timeString += this.addLeadingZero(seconds);

        return timeString;
    }

    private transformDays(days: number): string {
        if (days > 1) {
            return days + ' days ';
        } else if (days === 1) {
            return days + ' day ';
        } else {
            return '';
        }
    }

    private addLeadingZero(number: number): string {
        let numberOut = number.toString();
        if (number < 10) {
            numberOut = '0' + numberOut;
        }
        return numberOut;
    }
}
