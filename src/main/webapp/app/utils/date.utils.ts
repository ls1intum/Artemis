import dayjs from 'dayjs/esm';

/**
 * Converts a date in order to send it to the server
 * @param date the date either as proper date or string representation
 */
export function convertDateFromClient(date?: dayjs.Dayjs): string | undefined {
    // Result completionDate is a dayjs object -> toJSON.
    return date && dayjs.isDayjs(date) ? date.toJSON() : date;
}

/**
 * converts a date sent by the server to a proper dayjs object
 * @param date the date sent by the server
 */
export function convertDateFromServer(date?: dayjs.Dayjs): dayjs.Dayjs | undefined {
    return date ? dayjs(date) : undefined;
}
