import { Pipe, PipeTransform } from '@angular/core';

type Duration = {
    days: number;
    hours: number;
    minutes: number;
    seconds: number;
    millis: number;
    micros: number;
    nanos: number;
};

@Pipe({ name: 'artemisDurationFromNanos' })
export class ArtemisDurationFromNanosPipe implements PipeTransform {
    private readonly NANOS_IN_MICRO = 1000;
    private readonly NANOS_IN_MILLI = 1000 * this.NANOS_IN_MICRO;
    private readonly NANOS_IN_SECOND = 1000 * this.NANOS_IN_MILLI;
    private readonly NANOS_IN_MINUTE = 60 * this.NANOS_IN_SECOND;
    private readonly NANOS_IN_HOUR = 60 * this.NANOS_IN_MINUTE;
    private readonly NANOS_IN_DAY = 24 * this.NANOS_IN_HOUR;

    /**
     * Convert nanos to a human-readable duration format:
     * "xx unit yy unit", where the two highest units are shown.
     *
     * Only positive durations are supported.
     * Negative ones will be shown as zero nanoseconds.
     *
     * @param nanos the number of nanoseconds that are turned into a human-readable format
     */
    transform(nanos: number): string {
        nanos = Math.max(0, nanos ?? 0);

        const duration = this.secondsToDuration(nanos);
        return ArtemisDurationFromNanosPipe.handleShortFormat(duration);
    }

    /**
     * Converts the duration in nanos into a duration of full days, hours, minutes, seconds, millis, micros, and nanos.
     * @param nanos the total nanos of the duration.
     */
    public secondsToDuration(nanos: number): Duration {
        const days = Math.floor(nanos / this.NANOS_IN_DAY);
        const hours = Math.floor((nanos % this.NANOS_IN_DAY) / this.NANOS_IN_HOUR);
        const minutes = Math.floor((nanos % this.NANOS_IN_HOUR) / this.NANOS_IN_MINUTE);
        const seconds = Math.floor((nanos % this.NANOS_IN_MINUTE) / this.NANOS_IN_SECOND);
        const millis = Math.floor((nanos % this.NANOS_IN_SECOND) / this.NANOS_IN_MILLI);
        const micros = Math.floor((nanos % this.NANOS_IN_MILLI) / this.NANOS_IN_MICRO);
        nanos = nanos % this.NANOS_IN_MICRO;

        return {
            days,
            hours,
            minutes,
            seconds,
            millis,
            micros,
            nanos,
        } as Duration;
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
        } else if (duration.minutes > 0) {
            return `${duration.minutes}min ${duration.seconds}s`;
        } else if (duration.seconds > 0) {
            return `${duration.seconds}s ${duration.millis}ms`;
        } else if (duration.millis > 0) {
            return `${duration.millis}ms ${duration.micros}μs`;
        } else if (duration.micros > 0) {
            return `${duration.micros}μs ${duration.nanos}ns`;
        } else {
            return `${duration.nanos}ns`;
        }
    }
}
