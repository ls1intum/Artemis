import { CourseNotificationChannelSetting } from 'app/communication/shared/entities/course-notification/course-notification-channel-setting';

export class CourseNotificationSettingSpecification {
    identifier: string;
    typeId: number;
    channelSetting: CourseNotificationChannelSetting;

    constructor(identifier: string, typeId: number, channelSetting: CourseNotificationChannelSetting) {
        this.identifier = identifier;
        this.typeId = typeId;
        this.channelSetting = channelSetting;
    }
}
