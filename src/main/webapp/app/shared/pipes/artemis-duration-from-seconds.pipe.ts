import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'artemisDurationFromSeconds' })
export class ArtemisDurationFromSecondsPipe implements PipeTransform {
    /**
     * Convert seconds to a human-readable duration format (mm:ss).
     * @param seconds {number}
     */
    transform(seconds: number | undefined): string {
        if (seconds == undefined) {
            return '00:00';
        }

        const minutes = Math.floor(seconds / 60);
        seconds = seconds - minutes * 60;

        let minutesOut = minutes.toString();
        if (minutes < 10) {
            minutesOut = '0' + minutes.toString();
        }
        let secondsOut = seconds.toString();
        if (seconds < 10) {
            secondsOut = '0' + seconds.toString();
        }

        return minutesOut + ':' + secondsOut;
    }
}
