import { Component, effect, inject, input, output, untracked } from '@angular/core';
import { CourseNotification } from 'app/communication/shared/entities/course-notification/course-notification';
import { CourseNotificationService } from 'app/communication/course-notification/course-notification.service';
import { IconDefinition } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CommonModule } from '@angular/common';
import { addPublicFilePrefix } from 'app/app.constants';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { DomSanitizer } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';

/**
 * Component for displaying a single course notification.
 * Renders notification content, metadata, and handles interaction events.
 * Supports various display modes through input properties.
 */
@Component({
    selector: 'jhi-course-notification',
    imports: [FaIconComponent, ProfilePictureComponent, TranslateDirective, CommonModule, RouterLink],
    templateUrl: './course-notification.component.html',
    styleUrls: ['./course-notification.component.scss'],
})
export class CourseNotificationComponent {
    private readonly courseNotificationService: CourseNotificationService = inject(CourseNotificationService);
    private readonly markdownService: ArtemisMarkdownService = inject(ArtemisMarkdownService);
    private readonly sanitizer: DomSanitizer = inject(DomSanitizer);

    readonly onCloseClicked = output<void>();

    readonly courseNotification = input.required<CourseNotification>();
    readonly isUnseen = input<boolean>(false);
    readonly isShowClose = input<boolean>(false);
    readonly isHideTime = input<boolean>(false);
    readonly isRedirectToUrl = input<boolean>(false);
    readonly displayTimeInMilliseconds = input<number | undefined>(undefined);

    protected faIcon: IconDefinition;
    protected notificationParameters: { [key: string]: unknown };
    protected notificationType: string;
    protected notificationInitialized: boolean = false;
    protected notificationUrl: { link: string[]; queryParams: Record<string, string> };
    protected notificationTimeTranslationKey: string;
    protected notificationTimeTranslationParameters: { [key: string]: unknown };

    // Icons
    protected faTimes = faTimes;

    // Needed for communication notifications
    protected isShowProfilePicture: boolean = false;
    protected authorName: string | undefined;
    protected authorId: number | undefined;
    protected authorImageUrl: string | undefined;
    protected isAuthorBot: boolean = false;

    constructor() {
        effect(() => {
            const notification = this.courseNotification();
            untracked(() => {
                this.faIcon = this.courseNotificationService.getIconFromType(notification.notificationType);
                // For translations, we pass all parameters and the course name and id so they can automatically be used.
                this.notificationParameters = {
                    ...Object.entries(notification.parameters!).reduce(
                        (acc, [key, value]) => {
                            if (!value || !CourseNotificationService.NOTIFICATION_MARKDOWN_PARAMETERS.includes(key)) {
                                acc[key] = value;
                            } else {
                                let sanitized = this.sanitizer.sanitize(1, this.markdownService.safeHtmlForPostingMarkdown(value!.toString())) || '';
                                // Iteratively strip HTML tags to prevent incomplete sanitization (e.g. nested tags like <scr<script>ipt>)
                                let previous: string;
                                do {
                                    previous = sanitized;
                                    sanitized = sanitized.replace(/<[^>]*>/g, '');
                                } while (sanitized !== previous);
                                acc[key] = sanitized;
                            }

                            return acc;
                        },
                        {} as Record<string, any>,
                    ),
                    courseName: notification.courseName,
                    courseId: notification.courseId,
                };
                this.notificationType = notification.notificationType!;
                this.notificationUrl = this.parseUrlToRouterObject(notification.relativeWebAppUrl!);
                this.notificationTimeTranslationKey = this.courseNotificationService.getDateTranslationKey(notification);
                this.notificationTimeTranslationParameters = this.courseNotificationService.getDateTranslationParams(notification);
                if ('authorName' in this.notificationParameters && 'authorImageUrl' in this.notificationParameters && 'authorId' in this.notificationParameters) {
                    this.authorName = this.notificationParameters.authorName as string;
                    this.authorId = this.notificationParameters.authorId as number;
                    this.authorImageUrl = this.notificationParameters.authorImageUrl as string;
                    this.isAuthorBot = this.notificationParameters.authorIsBot === true;
                    this.isShowProfilePicture = true;
                } else if ('replyAuthorName' in this.notificationParameters && 'replyImageUrl' in this.notificationParameters && 'replyAuthorId' in this.notificationParameters) {
                    this.authorName = this.notificationParameters.replyAuthorName as string;
                    this.authorId = this.notificationParameters.replyAuthorId as number;
                    this.authorImageUrl = this.notificationParameters.replyImageUrl as string;
                    this.isAuthorBot = this.notificationParameters.replyIsBot === true;
                    this.isShowProfilePicture = true;
                } else {
                    this.isAuthorBot = false;
                    this.isShowProfilePicture = false;
                }
                this.notificationInitialized = true;
            });
        });
    }

    /**
     * Handles the close button click event.
     * Emits an event to notify parent components.
     */
    protected closeClicked() {
        this.onCloseClicked.emit();
    }

    /**
     * Parses an url to an object that can be interpreted by a routerLink
     * @param url The url to parse as a string
     * @return An object that can be interpreted by a routerLink
     */
    private parseUrlToRouterObject(url: string): { link: string[]; queryParams: Record<string, string> } {
        if (url === undefined) {
            return { link: [''], queryParams: {} };
        }

        const [path, queryString] = url.split('?');
        const queryParams: Record<string, string> = {};

        if (queryString) {
            queryString.split('&').forEach((param) => {
                const [key, value] = param.split('=');
                queryParams[key] = decodeURIComponent(value || '');
            });
        }

        return { link: [path], queryParams };
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
