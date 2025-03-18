import { Component, effect, inject, input, output } from '@angular/core';
import { CourseNotification } from 'app/entities/course-notification/course-notification';
import { CourseNotificationService } from 'app/communication/course-notification/course-notification.service';
import { IconDefinition } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CommonModule } from '@angular/common';
import { addPublicFilePrefix } from 'app/app.constants';
import { faTimes } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-course-notification',
    imports: [FaIconComponent, ProfilePictureComponent, TranslateDirective, CommonModule],
    templateUrl: './course-notification.component.html',
    styleUrls: ['./course-notification.component.scss'],
})
export class CourseNotificationComponent {
    private readonly courseNotificationService: CourseNotificationService = inject(CourseNotificationService);

    readonly onCloseClicked = output<void>();

    readonly courseNotification = input.required<CourseNotification>();
    readonly isUnseen = input<boolean>(false);
    readonly isShowClose = input<boolean>(false);
    readonly displayTimeInMilliseconds = input<number | undefined>(undefined);

    protected faIcon: IconDefinition;
    protected notificationParameters: { [key: string]: unknown };
    protected notificationType: string;
    protected notificationTimeTranslationKey: string;
    protected notificationTimeTranslationParameters: { [key: string]: unknown };

    // Icons
    protected faTimes = faTimes;

    // Needed for communication notifications
    protected isShowProfilePicture: boolean = false;
    protected authorName: string | undefined;
    protected authorId: number | undefined;
    protected authorImageUrl: string | undefined;

    constructor() {
        effect(() => {
            this.faIcon = this.courseNotificationService.getIconFromType(this.courseNotification().notificationType);
            // For translations, we pass all parameters and the course name and id so they can automatically be used.
            this.notificationParameters = {
                ...this.courseNotification().parameters,
                courseName: this.courseNotification().courseName,
                courseId: this.courseNotification().courseId,
            };
            this.notificationType = this.courseNotification().notificationType!;
            this.notificationTimeTranslationKey = this.courseNotificationService.getDateTranslationKey(this.courseNotification());
            this.notificationTimeTranslationParameters = this.courseNotificationService.getDateTranslationParams(this.courseNotification());
            if ('authorName' in this.notificationParameters && 'authorImageUrl' in this.notificationParameters && 'authorId' in this.notificationParameters) {
                this.authorName = this.notificationParameters.authorName as string;
                this.authorId = this.notificationParameters.authorId as number;
                this.authorImageUrl = this.notificationParameters.authorImageUrl as string;
                this.isShowProfilePicture = true;
            } else {
                this.isShowProfilePicture = false;
            }
        });
    }

    protected closeClicked() {
        this.onCloseClicked.emit();
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
