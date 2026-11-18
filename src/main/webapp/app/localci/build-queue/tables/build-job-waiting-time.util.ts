import dayjs from 'dayjs/esm';

export function buildJobWaitingTime(submissionDate: dayjs.Dayjs | undefined, now = dayjs()): string {
    if (!submissionDate) {
        return '-';
    }
    const diffSeconds = Math.max(0, now.diff(submissionDate, 'seconds'));

    if (diffSeconds < 60) {
        return `${diffSeconds}s`;
    } else if (diffSeconds < 3600) {
        const minutes = Math.floor(diffSeconds / 60);
        const seconds = diffSeconds % 60;
        return `${minutes}m ${seconds}s`;
    } else {
        const hours = Math.floor(diffSeconds / 3600);
        const minutes = Math.floor((diffSeconds % 3600) / 60);
        return `${hours}h ${minutes}m`;
    }
}
