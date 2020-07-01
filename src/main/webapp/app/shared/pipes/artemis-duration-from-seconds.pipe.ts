import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'artemisDurationFromSeconds' })
export class ArtemisDurationFromSecondsPipe implements PipeTransform {
    /**
     * Convert seconds to a human-readable duration format.
     * @param seconds {number}
     */
    transform(seconds: number): string {
        const hours = Math.floor(seconds / 3600);
        const minutes = Math.floor((seconds - hours * 3600) / 60);
        seconds = seconds - hours * 3600 - minutes * 60;

        let hoursOut = hours.toString();
        if (hours < 10) {
            hoursOut = '0' + hours.toString();
        }
        let minutesOut = minutes.toString();
        if (minutes < 10) {
            minutesOut = '0' + minutes.toString();
        }
        let secondsOut = seconds.toString();
        if (seconds < 10) {
            secondsOut = '0' + seconds.toString();
        }

        return hoursOut + ':' + minutesOut + ':' + secondsOut;
    }
}
