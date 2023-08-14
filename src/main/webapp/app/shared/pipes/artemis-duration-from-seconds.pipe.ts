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
     * If short is true: "xx unit yy unit", where the two highest units are shown.
     * If the time is between 10 minutes and one hour, only the minutes are shown.
     *
     * Otherwise: "1d 11h 7min 6s", where the zero-valued parts are left out,
     * except in case of `seconds=0`, which will be shown as "0s".
     *
     * Only positive durations are supported.
     * Negative ones will be shown as zero seconds.
     *
     * @param seconds the number of seconds that are turned into a human-readable format
     * @param short allows the format to be shortened
     */
    transform(seconds: number, short = false): string {
        seconds = Math.max(0, seconds ?? 0);

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

    /**
     * Converts the duration into its total number of seconds.
     * @param duration for which the total number of seconds should be determined.
     */
    public durationToSeconds(duration: Duration): number {
        return duration.days * this.secondsInDay + duration.hours * this.secondsInHour + duration.minutes * this.secondsInMinute + duration.seconds;
    }

    /**
     * Converts the given duration into a human-readable short format as required by {@link transform}.
     * @param duration that should be converted into a human-readable format.
     */
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

    /**
     * Converts the given duration into a human-readable long format as required by {@link transform}.
     * @param duration that should be converted into a human-readable format.
     */
    private static handleLongFormat(duration: Duration): string {
        const result = [];

        if (duration.days > 0) {
            result.push(`${duration.days}d`);
        }
        if (duration.hours > 0) {
            result.push(`${duration.hours}h`);
        }
        if (duration.minutes > 0) {
            result.push(`${duration.minutes}min`);
        }
        if (duration.seconds > 0 || result.length === 0) {
            result.push(`${duration.seconds}s`);
        }

        return result.join(' ');
    }
}
