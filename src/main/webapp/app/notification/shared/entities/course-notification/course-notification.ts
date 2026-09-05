import dayjs from 'dayjs/esm';
import { CourseNotificationCategory } from 'app/notification/shared/entities/course-notification/course-notification-category';
import { CourseNotificationViewingStatus } from 'app/notification/shared/entities/course-notification/course-notification-viewing-status';
import { CourseNotificationPayload, CourseNotificationPayloadByType } from 'app/notification/shared/entities/course-notification/course-notification-payload';

export class CourseNotification {
    public notificationId?: number;
    public courseId?: number;
    public courseTitle?: string;
    public courseIconUrl?: string;
    public notificationType?: string;
    public category?: CourseNotificationCategory;
    public status?: CourseNotificationViewingStatus;
    public creationDate?: dayjs.Dayjs;
    public payload?: CourseNotificationPayload;
    public relativeWebAppUrl?: string;

    constructor(
        notificationId: number,
        courseId: number,
        notificationType: string,
        courseNotificationCategory: CourseNotificationCategory,
        status: CourseNotificationViewingStatus,
        creationDate: dayjs.Dayjs,
        courseTitle: string | undefined,
        courseIconUrl: string | undefined,
        payload: CourseNotificationPayload,
        relativeWebAppUrl: string,
    ) {
        this.status = status;
        this.notificationId = notificationId;
        this.courseId = courseId;
        this.courseTitle = courseTitle;
        this.courseIconUrl = courseIconUrl;
        this.notificationType = notificationType;
        this.category = courseNotificationCategory;
        this.creationDate = creationDate;
        this.payload = payload;
        this.relativeWebAppUrl = relativeWebAppUrl;
    }
}

/**
 * Reads the payload of a notification as the type that notification carries.
 *
 * The server decides the payload from the notification type, so this checks that discriminator before handing the
 * payload back. It is the one place that relates the two, which is what lets every caller read real properties:
 *
 * ```ts
 * const post = payloadOf(notification, 'newPostNotification');
 * if (post?.channelId === openConversationId) { ... }
 * ```
 *
 * @param notification the notification to read
 * @param notificationType the type whose payload the caller wants
 * @returns the payload when the notification is of that type, otherwise undefined
 */
export function payloadOf<T extends keyof CourseNotificationPayloadByType>(notification: CourseNotification, notificationType: T): CourseNotificationPayloadByType[T] | undefined {
    if (notification.notificationType !== notificationType || notification.payload === undefined) {
        return undefined;
    }
    return notification.payload;
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
    // Idempotent: an already-resolved numeric member is returned as-is (a valid member has a reverse-mapped name string).
    if (typeof name === 'number') {
        return typeof enumObject[name] === 'string' ? name : undefined;
    }
    const member = enumObject[name];
    return typeof member === 'number' ? member : undefined;
}
