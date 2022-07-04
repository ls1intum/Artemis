import dayjs from 'dayjs/esm';

/**
 * Converts a date in order to send it to the server
 * @param date the date either as proper date or string representation
 * @return the json representation of the date or the current representation if the date is not a dayjs date
 */
export function convertDateFromClient(date?: dayjs.Dayjs): string | undefined {
    if (!dayjs(date).isValid()) {
        return undefined;
    }
    return date && dayjs.isDayjs(date) ? date.toJSON() : date;
}

/**
 * converts a date sent by the server to a proper dayjs object
 * @param date the date sent by the server
 * @return the date as dayjs object if it is defined
 */
export function convertDateFromServer(date?: dayjs.Dayjs): dayjs.Dayjs | undefined {
    return date ? dayjs(date) : undefined;
}
