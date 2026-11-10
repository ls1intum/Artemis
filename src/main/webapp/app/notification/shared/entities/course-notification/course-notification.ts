import dayjs from 'dayjs/esm';
import { CourseNotification as CourseNotificationDto } from 'app/openapi/model/course-notification';
import { convertDateStringFromServer } from 'app/foundation/util/date.utils';
import { CourseNotificationCategory } from 'app/notification/shared/entities/course-notification/course-notification-category';
import { CourseNotificationViewingStatus } from 'app/notification/shared/entities/course-notification/course-notification-viewing-status';
import { CourseNotificationPayload } from 'app/notification/shared/entities/course-notification/course-notification-payload';

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
        courseNotificationCategory: CourseNotificationCategory | undefined,
        status: CourseNotificationViewingStatus | undefined,
        creationDate: dayjs.Dayjs | undefined,
        courseTitle: string | undefined,
        courseIconUrl: string | undefined,
        payload: CourseNotificationPayload | undefined,
        relativeWebAppUrl: string | undefined,
        public readonly wire?: CourseNotificationDto,
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

    /** Keeps the generated discriminant/payload correlation while exposing the existing Dayjs/numeric UI model. */
    static fromServer(notification: CourseNotificationDto): CourseNotification {
        return new CourseNotification(
            notification.notificationId,
            notification.courseId,
            notification.notificationType,
            courseNotificationEnumValueFromName(CourseNotificationCategory, notification.category),
            courseNotificationEnumValueFromName(CourseNotificationViewingStatus, notification.status),
            convertDateStringFromServer(notification.creationDate),
            notification.courseTitle,
            notification.courseIconUrl,
            notification.payload,
            notification.relativeWebAppUrl,
            notification,
        );
    }
}

/**
 * Reads the generated envelope as its concrete notification branch.
 *
 * The server decides the payload from the notification type, so this checks that discriminator before handing the
 * payload back. It is the one place that relates the two, which is what lets every caller read real properties:
 *
 * ```ts
 * const post = notificationOf(notification, 'newPostNotification')?.payload;
 * if (post?.channelId === openConversationId) { ... }
 * ```
 *
 * @param notification the notification to read
 * @param notificationType the type whose payload the caller wants
 * @returns the correlated generated envelope when the notification is of that type, otherwise undefined
 */
export function notificationOf<T extends CourseNotificationDto['notificationType']>(
    notification: CourseNotification,
    notificationType: T,
): Extract<CourseNotificationDto, { notificationType: T }> | undefined {
    if (notification.notificationType !== notificationType) {
        return undefined;
    }
    const wire = notification.wire;
    return wire && hasNotificationType(wire, notificationType) ? wire : undefined;
}

function hasNotificationType<T extends CourseNotificationDto['notificationType']>(
    notification: CourseNotificationDto,
    notificationType: T,
): notification is Extract<CourseNotificationDto, { notificationType: T }> {
    return notification.notificationType === notificationType;
}

/**
 * Resolves a numeric enum member from the enum NAME the server sends over the wire.
 *
 * {@link CourseNotificationCategory} and {@link CourseNotificationViewingStatus} are numeric enums, but
 * Jackson serializes them by constant name (e.g. "GENERAL", "SEEN"). The generated wire model carries
 * string names in `category`/`status`; the UI model uses numeric members. This performs the
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
