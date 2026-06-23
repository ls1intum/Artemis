import { Component, effect, inject, input, output, signal, untracked } from '@angular/core';
import { CourseNotification } from 'app/notification/shared/entities/course-notification/course-notification';
import { CourseNotificationService } from 'app/notification/course-notification/course-notification.service';
import { IconDefinition } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CommonModule } from '@angular/common';
import { addPublicFilePrefix } from 'app/app.constants';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { renderPostingMarkdownToHtml } from 'app/foundation/util/markdown-render.util';
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

    readonly onCloseClicked = output<void>();

    readonly courseNotification = input.required<CourseNotification>();
    readonly isUnseen = input<boolean>(false);
    readonly isShowClose = input<boolean>(false);
    readonly isHideTime = input<boolean>(false);
    readonly isRedirectToUrl = input<boolean>(false);
    readonly displayTimeInMilliseconds = input<number | undefined>(undefined);

    protected readonly faIcon = signal<IconDefinition>(undefined!);
    protected readonly notificationParameters = signal<{ [key: string]: unknown }>(undefined!);
    protected readonly notificationType = signal<string>(undefined!);
    protected readonly notificationInitialized = signal<boolean>(false);
    protected readonly notificationUrl = signal<{ link: string[]; queryParams: Record<string, string> }>(undefined!);
    protected readonly notificationTimeTranslationKey = signal<string>(undefined!);
    protected readonly notificationTimeTranslationParameters = signal<{ [key: string]: unknown }>(undefined!);

    // Icons
    protected faTimes = faTimes;

    // Needed for communication notifications
    protected readonly isShowProfilePicture = signal<boolean>(false);
    protected readonly authorName = signal<string | undefined>(undefined);
    protected readonly authorId = signal<number | undefined>(undefined);
    protected readonly authorImageUrl = signal<string | undefined>(undefined);
    protected readonly isAuthorBot = signal<boolean>(false);

    constructor() {
        effect(() => {
            const notification = this.courseNotification();
            untracked(() => {
                void this.initializeNotification(notification);
            });
        });
    }

    /**
     * Builds the translation parameters and metadata signals for the given notification. Markdown-valued
     * parameters are rendered and reduced to plain text via the lazily-loaded markdown pipeline, so this
     * runs asynchronously (the pipeline is no longer part of the eager bundle).
     */
    private async initializeNotification(notification: CourseNotification): Promise<void> {
        this.faIcon.set(this.courseNotificationService.getIconFromType(notification.notificationType));
        // For translations, we pass all parameters and the course name and id so they can automatically be used.
        const notificationParameters: { [key: string]: unknown } = {
            courseName: notification.courseName,
            courseId: notification.courseId,
        };
        for (const [key, value] of Object.entries(notification.parameters ?? {})) {
            if (!value || !CourseNotificationService.NOTIFICATION_MARKDOWN_PARAMETERS.includes(key)) {
                notificationParameters[key] = value;
            } else {
                // Render markdown, then iteratively strip HTML tags to plain text (handles nested tags like
                // <scr<script>ipt>). The conversion util already sanitizes the HTML via DOMPurify. If the lazy
                // markdown chunk fails to load, fall back to the raw value rather than leak an unhandled rejection.
                let sanitized: string;
                try {
                    sanitized = await renderPostingMarkdownToHtml(value!.toString());
                } catch {
                    sanitized = value!.toString();
                }
                let previous: string;
                do {
                    previous = sanitized;
                    sanitized = sanitized.replace(/<[^>]*>/g, '');
                } while (sanitized !== previous);
                notificationParameters[key] = sanitized;
            }
        }
        // A newer notification may have arrived while awaiting the lazy markdown render; discard this stale run.
        if (this.courseNotification() !== notification) {
            return;
        }
        this.notificationParameters.set(notificationParameters);
        this.notificationType.set(notification.notificationType!);
        this.notificationUrl.set(this.parseUrlToRouterObject(notification.relativeWebAppUrl!));
        this.notificationTimeTranslationKey.set(this.courseNotificationService.getDateTranslationKey(notification));
        this.notificationTimeTranslationParameters.set(this.courseNotificationService.getDateTranslationParams(notification));
        if ('authorName' in notificationParameters && 'authorImageUrl' in notificationParameters && 'authorId' in notificationParameters) {
            this.authorName.set(notificationParameters.authorName as string);
            this.authorId.set(notificationParameters.authorId as number);
            this.authorImageUrl.set(notificationParameters.authorImageUrl as string);
            this.isAuthorBot.set(notificationParameters.authorIsBot === true);
            this.isShowProfilePicture.set(true);
        } else if ('replyAuthorName' in notificationParameters && 'replyImageUrl' in notificationParameters && 'replyAuthorId' in notificationParameters) {
            this.authorName.set(notificationParameters.replyAuthorName as string);
            this.authorId.set(notificationParameters.replyAuthorId as number);
            this.authorImageUrl.set(notificationParameters.replyImageUrl as string);
            this.isAuthorBot.set(notificationParameters.replyIsBot === true);
            this.isShowProfilePicture.set(true);
        } else {
            this.isAuthorBot.set(false);
            this.isShowProfilePicture.set(false);
        }
        this.notificationInitialized.set(true);
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
