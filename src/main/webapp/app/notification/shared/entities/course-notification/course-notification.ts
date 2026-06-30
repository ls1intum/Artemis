import dayjs from 'dayjs/esm';
import { CourseNotificationCategory } from 'app/notification/shared/entities/course-notification/course-notification-category';
import { CourseNotificationViewingStatus } from 'app/notification/shared/entities/course-notification/course-notification-viewing-status';

export class CourseNotification {
    public notificationId?: number;
    public courseId?: number;
    public courseName?: string;
    public courseIconUrl?: string;
    public notificationType?: string;
    public category?: CourseNotificationCategory;
    public status?: CourseNotificationViewingStatus;
    public creationDate?: dayjs.Dayjs;
    public parameters?: Record<string, string | number | boolean | undefined>;
    public relativeWebAppUrl?: string;

    constructor(
        notificationId: number,
        courseId: number,
        notificationType: string,
        courseNotificationCategory: CourseNotificationCategory,
        status: CourseNotificationViewingStatus,
        creationDate: dayjs.Dayjs,
        parameters: Record<string, string | number | boolean | undefined>,
        relativeWebAppUrl: string,
    ) {
        this.status = status;
        this.notificationId = notificationId;
        this.courseId = courseId;
        if (parameters['courseTitle']) {
            this.courseName = parameters['courseTitle'] as string;
        }
        if (parameters['courseIconUrl'] !== undefined) {
            this.courseIconUrl = parameters['courseIconUrl'] as string;
        }
        this.notificationType = notificationType;
        this.category = courseNotificationCategory;
        this.creationDate = creationDate;
        this.parameters = parameters;
        this.relativeWebAppUrl = relativeWebAppUrl;
    }
}

/**
 * Resolves a numeric enum member from the enum NAME the server sends over the wire.
 *
 * {@link CourseNotificationCategory} and {@link CourseNotificationViewingStatus} are numeric enums, but
 * Jackson serializes them by constant name (e.g. "GENERAL", "SEEN"). The raw notification therefore carries
 * a string in `category`/`status` even though the model types them as the enum. This performs the
 * name → numeric-member reverse lookup, returning `undefined` for an unknown or missing name instead of
 * fabricating an invalid enum value.
 *
 * @param enumObject the numeric enum object (e.g. {@link CourseNotificationCategory})
 * @param name the value received from the server (a constant name at runtime), or `undefined`
 */
export function courseNotificationEnumValueFromName<E extends number>(enumObject: { [key: string]: E | string }, name: string | E | undefined): E | undefined {
    if (name === undefined) {
        return undefined;
    }
    const member = enumObject[name];
    return typeof member === 'number' ? member : undefined;
}
