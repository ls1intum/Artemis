import { Pipe, PipeTransform } from '@angular/core';

type Duration = {
    days: number;
    hours: number;
    minutes: number;
    seconds: number;
};

@Pipe({ name: 'artemisDurationFromSeconds' })
export class ArtemisDurationFromSecondsPipe implements PipeTransform {
    private readonly SECONDS_IN_DAY = 60 * 60 * 24;
    private readonly SECONDS_IN_HOUR = 60 * 60;
    private readonly SECONDS_IN_MINUTE = 60;

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
    transform(seconds: number, short = false, isExam = false): string {
        seconds = Math.max(0, seconds ?? 0);

        const duration = this.secondsToDuration(seconds);

        if (short) {
            return ArtemisDurationFromSecondsPipe.handleShortFormat(duration);
        } else {
            return ArtemisDurationFromSecondsPipe.handleLongFormat(duration, isExam);
        }
    }

    /**
     * Converts the duration in seconds into a duration of full days, hours, minutes, and seconds.
     * @param seconds the total seconds of the duration.
     */
    public secondsToDuration(seconds: number): Duration {
        const days = Math.floor(seconds / this.SECONDS_IN_DAY);
        const hours = Math.floor((seconds % this.SECONDS_IN_DAY) / this.SECONDS_IN_HOUR);
        const minutes = Math.floor((seconds % this.SECONDS_IN_HOUR) / this.SECONDS_IN_MINUTE);
        seconds = seconds % this.SECONDS_IN_MINUTE;

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
        return duration.days * this.SECONDS_IN_DAY + duration.hours * this.SECONDS_IN_HOUR + duration.minutes * this.SECONDS_IN_MINUTE + duration.seconds;
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
     * @param isExam determines if this format is used during the exam, so suffixes like 'd,h,min,s' are not used.
     */
    private static handleLongFormat(duration: Duration, isExam = false): string {
        const result = [];

        if (duration.days > 0) {
            result.push(isExam ? `${duration.days}:`.padStart(3, '0') : `${duration.days}d`);
        }
        if (duration.hours > 0) {
            result.push(isExam ? `${duration.hours}:`.padStart(3, '0') : `${duration.hours}h`);
        }
        if (duration.minutes > 0) {
            result.push(isExam ? `${duration.minutes}:`.padStart(3, '0') : `${duration.minutes}min`);
        }
        // if we are in the exam, we include zeros, otherwise we don't
        if (result.length === 0 || (isExam && duration.seconds >= 0) || (!isExam && duration.seconds > 0)) {
            result.push(isExam ? `${duration.seconds}`.padStart(2, '0') : `${duration.seconds}s`);
        }

        return result.join(isExam ? '' : ' ');
    }
}
