import { Component, effect, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseNotificationComponent } from 'app/communication/course-notification/course-notification/course-notification.component';
import dayjs from 'dayjs/esm';
import { CourseNotification } from 'app/communication/shared/entities/course-notification/course-notification';
import { CourseNotificationChannelSetting } from 'app/communication/shared/entities/course-notification/course-notification-channel-setting';
import { CourseNotificationChannel } from 'app/communication/shared/entities/course-notification/course-notification-channel';
import { CourseNotificationSettingSpecification } from 'app/communication/shared/entities/course-notification/course-notification-setting-specification';
import { CourseNotificationCategory } from 'app/communication/shared/entities/course-notification/course-notification-category';
import { CourseNotificationViewingStatus } from 'app/communication/shared/entities/course-notification/course-notification-viewing-status';
import { CourseNotificationService } from 'app/communication/course-notification/course-notification.service';

/**
 * Component for configuring notification settings for a specific notification type.
 * Displays a preview notification and allows toggling different notification channels.
 */
@Component({
    selector: 'jhi-course-notification-setting-specification-card',
    imports: [TranslateDirective, CourseNotificationComponent, FormsModule],
    templateUrl: './course-notification-setting-specification-card.component.html',
    styleUrls: ['./course-notification-setting-specification-card.component.scss'],
})
export class CourseNotificationSettingSpecificationCardComponent {
    readonly settingSpecification = input.required<CourseNotificationSettingSpecification>();

    readonly onOptionChanged = output<CourseNotificationSettingSpecification>();

    protected titleLangKey: string;
    protected typeId: number;
    protected mockNotification: CourseNotification;
    protected channels: CourseNotificationChannelSetting;
    protected readonly possibleChannels = Object.values(CourseNotificationChannel);

    constructor() {
        effect(() => {
            this.titleLangKey = 'artemisApp.courseNotification.' + this.settingSpecification().identifier + '.settingsTitle';
            this.typeId = this.settingSpecification().typeId;

            // Note: Add translation values to the parameters object here to make them show up in preview notifications
            this.mockNotification = new CourseNotification(
                1,
                1,
                this.settingSpecification().identifier,
                CourseNotificationCategory.GENERAL,
                CourseNotificationViewingStatus.SEEN,
                dayjs(),
                {
                    authorName: 'Maria Muster',
                    courseName: 'Patterns in Software Engineering',
                    courseTitle: 'Patterns in Software Engineering',
                    postMarkdownContent: 'Can anybody tell me how to bake chocolate cookies?',
                    replyMarkdownContent: 'Can anybody tell me how to bake chocolate cookies?',
                    courseIconUrl: undefined,
                    channelName: 'tech-support',
                    exerciseTitle: 'Modeling 123',
                    unitName: 'Modeling 123',
                    groupTitle: 'Grp 123',
                },
                '/',
            );
            this.channels = this.settingSpecification().channelSetting;
        });
    }

    /**
     * Emits an event when a notification channel option is changed.
     * Creates a new specification object with the updated channel settings.
     */
    optionChanged() {
        this.onOptionChanged.emit(new CourseNotificationSettingSpecification(this.titleLangKey, this.typeId, this.channels));
    }

    /**
     * Determines if a specific notification channel should be disabled for this notification type.
     *
     * @param option - The notification channel to check
     * @param identifier - The notification type identifier
     * @returns Whether the channel option should be disabled
     */
    isDisabled(option: string, identifier: string) {
        return CourseNotificationService.DISABLE_NOTIFICATION_CHANNEL_TYPES[identifier]?.includes(option as CourseNotificationChannel) || false;
    }
}
