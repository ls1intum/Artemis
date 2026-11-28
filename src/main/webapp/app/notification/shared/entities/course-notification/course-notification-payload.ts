import { CourseNotification as CourseNotificationDto } from 'app/openapi/model/course-notification';

/** UI payloads come from the generated envelope branches; their wire fields are declared only by the server. */
export type CourseNotificationPayload = NonNullable<CourseNotificationDto['payload']>;
