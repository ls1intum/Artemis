import { Pipe, PipeTransform } from '@angular/core';

type Duration = {
    days: number;
    hours: number;
    minutes: number;
    seconds: number;
};

@Pipe({ name: 'artemisDurationFromSeconds' })
export class ArtemisDurationFromSecondsPipe implements PipeTransform {
    private readonly secondsInDay = 60 * 60 * 24;
    private readonly secondsInHour = 60 * 60;
    private readonly secondsInMinute = 60;

    /**
     * Convert seconds to a human-readable duration format:
     * If short is true: "xx unit yy unit", where only the two highest units are shown. If the time is between 10 minutes and one hour, only the minutes are shown
     * Otherwise: "1d 11h 7min 6s", where the days and hours are left out if their value and all higher values are zero
     * @param short? {boolean} allows the format to be shortened
     * @param seconds {number}
     */
    transform(seconds: number, short = false): string {
        if (!seconds || seconds <= 0) {
            return '0min 0s';
        }

        const duration = this.secondsToDuration(seconds);

        if (short) {
            return ArtemisDurationFromSecondsPipe.handleShortFormat(duration);
        } else {
            return ArtemisDurationFromSecondsPipe.handleLongFormat(duration);
        }
    }

    /**
     * Converts the duration in seconds into a duration of full days, hours, minutes, and seconds.
     * @param seconds the total seconds of the duration.
     */
    public secondsToDuration(seconds: number): Duration {
        const days = Math.floor(seconds / this.secondsInDay);
        const hours = Math.floor((seconds % this.secondsInDay) / this.secondsInHour);
        const minutes = Math.floor((seconds % this.secondsInHour) / this.secondsInMinute);
        seconds = seconds % this.secondsInMinute;

        return {
            days,
            hours,
            minutes,
            seconds,
        };
    }

    private static handleShortFormat(duration: Duration): string {
        if (duration.days > 0) {
            return `${duration.days}d ${duration.hours}h`;
        } else if (duration.hours > 0) {
            return `${duration.hours}h ${duration.minutes}min`;
        } else if (duration.minutes >= 10) {
            return `${duration.minutes}min`;
        } else {
            return `${duration.minutes}min ${duration.seconds}s`;
        }
    }

    private static handleLongFormat(duration: Duration): string {
        if (duration.days > 0) {
            return `${duration.days}d ${duration.hours}h ${duration.minutes}min ${duration.seconds}s`;
        } else if (duration.hours > 0) {
            return `${duration.hours}h ${duration.minutes}min ${duration.seconds}s`;
        } else {
            return `${duration.minutes}min ${duration.seconds}s`;
        }
    }
}
