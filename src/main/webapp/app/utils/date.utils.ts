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

function padWithZero(x: number): string {
    if (x < 10) {
        return '0' + x.toString();
    }
    return x.toString();
}

/**
 * Creates date without a time-zone in the ISO-8601 calendar system, such as 2007-12-03.
 *
 * Note: It does this WITHOUT any time zone conversion! The server is responsible for interpreting the date in the correct time zone.
 * Note: Useful if you want to send a date exactly to the server as the user has entered it.
 *
 * Converts to LocalDate in Java on the server side.
 *
 * @param date date to convert
 */
export function toISO8601DateString(date: Date | undefined | null) {
    if (date) {
        return date.getFullYear() + '-' + padWithZero(date.getMonth() + 1) + '-' + padWithZero(date.getDate());
    } else {
        return date;
    }
}

/**
 * Creates a date-time string without a time-zone in the ISO-8601 calendar system , such as 2007-12-03T10:15:30.
 *
 * Note: It does this WITHOUT any time zone conversion! The server is responsible for interpreting the date in the correct time zone.
 * Note: Useful if you want to send a date exactly to the server as the user has entered it.
 *
 * Converts to LocalDateTime in Java on the server side.
 *
 * @param date date to convert
 */
export function toISO8601DateTimeString(date: Date | undefined | null) {
    if (date) {
        return toISO8601DateString(date) + 'T' + padWithZero(date.getHours()) + ':' + padWithZero(date.getMinutes()) + ':' + padWithZero(date.getSeconds());
    } else {
        return date;
    }
}
