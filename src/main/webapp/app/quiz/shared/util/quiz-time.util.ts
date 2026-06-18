const SECONDS_PER_MINUTE = 60;
const SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE;
const SECONDS_PER_DAY = 24 * SECONDS_PER_HOUR;

export function formatQuizRelativeTime(remainingTimeSeconds: number): string {
    if (remainingTimeSeconds >= SECONDS_PER_DAY) {
        const days = Math.floor(remainingTimeSeconds / SECONDS_PER_DAY);
        const hours = Math.floor((remainingTimeSeconds % SECONDS_PER_DAY) / SECONDS_PER_HOUR);
        return hours > 0 ? `${days} d ${hours} h` : `${days} d`;
    }
    if (remainingTimeSeconds >= SECONDS_PER_HOUR) {
        const hours = Math.floor(remainingTimeSeconds / SECONDS_PER_HOUR);
        const minutes = Math.floor((remainingTimeSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE);
        return minutes > 0 ? `${hours} h ${minutes} min` : `${hours} h`;
    }
    if (remainingTimeSeconds > 210) {
        return Math.ceil(remainingTimeSeconds / SECONDS_PER_MINUTE) + ' min';
    }
    if (remainingTimeSeconds > 59) {
        return Math.floor(remainingTimeSeconds / SECONDS_PER_MINUTE) + ' min ' + (remainingTimeSeconds % SECONDS_PER_MINUTE) + ' s';
    }
    return remainingTimeSeconds + ' s';
}
