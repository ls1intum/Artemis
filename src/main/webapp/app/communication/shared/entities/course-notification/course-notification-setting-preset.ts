import { CourseNotificationSettingsMap } from 'app/communication/shared/entities/course-notification/course-notification-settings-map';

class CourseNotificationSettingPreset {
    identifier: string;
    typeId: number;
    presetMap: CourseNotificationSettingsMap;

    constructor(identifier: string, typeId: number, presetMap: CourseNotificationSettingsMap) {
        this.identifier = identifier;
        this.typeId = typeId;
        this.presetMap = presetMap;
    }
}

export { CourseNotificationSettingPreset };
