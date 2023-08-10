import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, IsActiveMatchOptions, Params, Router, UrlTree } from '@angular/router';
import { NotificationService } from 'app/shared/notification/notification.service';
import {
    LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE,
    NEW_MESSAGE_TITLE,
    NEW_REPLY_MESSAGE_TITLE,
    Notification,
    QUIZ_EXERCISE_STARTED_TITLE,
} from 'app/entities/notification.model';
import { GroupNotification } from 'app/entities/group-notification.model';
import { ExamExerciseUpdateService } from 'app/exam/manage/exam-exercise-update.service';
import { AlertService } from 'app/core/util/alert.service';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { faCheckDouble, faExclamationTriangle, faMessage, faTimes } from '@fortawesome/free-solid-svg-icons';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { RouteComponents } from 'app/shared/metis/metis.util';
import { NotificationSettingsService } from 'app/shared/user-settings/notification-settings/notification-settings.service';
import { translationNotFoundMessage } from 'app/core/config/translation.config';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-notification-popup',
    templateUrl: './notification-popup.component.html',
    styleUrls: ['./notification-popup.scss'],
})
export class NotificationPopupComponent implements OnInit {
    notifications: Notification[] = [];

    LiveExamExerciseUpdateNotificationTitleHtmlConst = LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE;
    QuizNotificationTitleHtmlConst = 'Quiz started';

    private studentExamExerciseIds: number[];

    // Icons
    faTimes = faTimes;
    faMessage = faMessage;
    faCheckDouble = faCheckDouble;
    faExclamationTriangle = faExclamationTriangle;

    constructor(
        private notificationService: NotificationService,
        private router: Router,
        private activatedRoute: ActivatedRoute,
        private notificationSettingsService: NotificationSettingsService,
        private examExerciseUpdateService: ExamExerciseUpdateService,
        private alertService: AlertService,
        private examParticipationService: ExamParticipationService,
        private artemisTranslatePipe: ArtemisTranslatePipe,
    ) {}

    ngOnInit(): void {
        this.notificationService.subscribeToSingleIncomingNotifications().subscribe((notification: Notification) => {
            this.addNotification(notification);
        });
    }

    /**
     * Removes the notification at the specified index from the notifications array.
     * @param index {number}
     */
    removeNotification(index: number): void {
        this.notifications.splice(index, 1);
    }

    /**
     * Navigate to the target (view) of the notification that the user clicked.
     * @param notification {Notification}
     */
    navigateToTarget(notification: Notification): void {
        const currentCourseId = this.activatedRoute.snapshot.queryParams['courseId'];
        const target = JSON.parse(notification.target!);
        const targetCourseId = target.course;
        const targetConversationId = target.conversation;

        if (notification.title === LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE) {
            this.examExerciseUpdateService.navigateToExamExercise(target.exercise);
        } else if (notification.title === NEW_REPLY_MESSAGE_TITLE || notification.title === NEW_MESSAGE_TITLE) {
            const queryParams: Params = MetisConversationService.getQueryParamsForConversation(targetConversationId);
            const routeComponents: RouteComponents = MetisConversationService.getLinkForConversation(targetCourseId);
            // check if component reload is needed
            if (currentCourseId === undefined || currentCourseId !== targetCourseId || this.isUnderMessagesTabOfSpecificCourse(targetCourseId)) {
                this.notificationService.forceComponentReload(routeComponents, queryParams);
            } else {
                this.router.navigate(routeComponents, { queryParams });
            }
        } else {
            this.router.navigateByUrl(this.notificationTargetRoute(notification));
        }
    }

    /**
     * Returns the translated text for the placeholder of the title of the provided notification.
     * If the notification is a legacy notification and therefor the title is not a placeholder
     * it just returns the provided text for the title
     * @param notification {Notification}
     */
    getNotificationTitleTranslation(notification: Notification): string {
        const translation = this.artemisTranslatePipe.transform(notification.title);
        if (translation?.includes(translationNotFoundMessage)) {
            return notification.title ?? 'No title found';
        }
        return translation;
    }

    /**
     * Returns the translated text for the placeholder of the notification text of the provided notification.
     * If the notification is a legacy notification and therefor the text is not a placeholder
     * it just returns the provided text for the notification text
     * @param notification {Notification}
     */
    getNotificationTextTranslation(notification: Notification): string {
        if (notification.textIsPlaceholder) {
            const translation = this.artemisTranslatePipe.transform(notification.text, { placeholderValues: this.getParsedPlaceholderValues(notification) });
            if (translation?.includes(translationNotFoundMessage)) {
                return notification.text ?? 'No text found';
            }
            return translation;
        } else {
            return notification.text ?? 'No text found';
        }
    }

    private getParsedPlaceholderValues(notification: Notification): string[] {
        if (notification.placeholderValues) {
            return JSON.parse(notification.placeholderValues);
        }
        return [];
    }

    private notificationTargetRoute(notification: Notification): UrlTree | string {
        if (notification.target) {
            const target = JSON.parse(notification.target);
            if (notification.title === LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE) {
                return this.router.createUrlTree([target.mainPage, target.course, target.entity, target.exam]);
            } else if (notification.title === QUIZ_EXERCISE_STARTED_TITLE && target.status) {
                return this.router.createUrlTree([target.mainPage, target.course, target.entity, target.id, target.status]);
            } else {
                return this.router.createUrlTree([target.mainPage, target.course, target.entity, target.id]);
            }
        }
        return this.router.url;
    }

    private addNotification(notification: Notification): void {
        // Only add a notification if it does not already exist.
        if (notification && !this.notifications.some(({ id }) => id === notification.id)) {
            if (notification.title === QUIZ_EXERCISE_STARTED_TITLE) {
                this.addQuizNotification(notification);
                this.setRemovalTimeout(notification);
            }
            if (notification.title === LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE) {
                this.checkIfNotificationAffectsCurrentStudentExamExercises(notification);
            }
            if (notification.title === NEW_MESSAGE_TITLE || notification.title === NEW_REPLY_MESSAGE_TITLE) {
                if (this.notificationSettingsService.isNotificationAllowedBySettings(notification)) {
                    this.addMessageNotification(notification);
                    this.setRemovalTimeout(notification);
                }
            }
        }
    }

    /**
     * Will add a notification about a new message or reply to the component's state.
     * @param notification {Notification}
     */
    private addMessageNotification(notification: Notification): void {
        if (notification.target) {
            const target = JSON.parse(notification.target);
            if (!this.isUnderMessagesTabOfSpecificCourse(target.course)) {
                this.notifications.unshift(notification);
            }
        }
    }

    /**
     * Check if user is under messages tab.
     * @returns {boolean} true if user is under messages tab, false otherwise
     */
    private isUnderMessagesTabOfSpecificCourse(targetCourseId: string): boolean {
        return this.router.url.includes(`courses/${targetCourseId}/messages`);
    }

    /**
     * Will add a notification about a started quiz to the component's state. The notification will
     * only be added if the user is not already on the target page (or the live participation page).
     * @param notification {Notification}
     */
    private addQuizNotification(notification: Notification): void {
        if (notification.target) {
            const target = JSON.parse(notification.target);
            target.entity = 'quiz-exercises';
            target.status = 'live';
            const notificationWithLiveQuizTarget = {
                target: JSON.stringify(target),
            } as GroupNotification;
            const matchOptions = { paths: 'exact', queryParams: 'exact', fragment: 'ignored', matrixParams: 'ignored' } as IsActiveMatchOptions; // corresponds to exact = true
            if (
                !this.router.isActive(this.notificationTargetRoute(notification), matchOptions) &&
                !this.router.isActive(this.notificationTargetRoute(notificationWithLiveQuizTarget) + '/live', matchOptions)
            ) {
                notification.target = notificationWithLiveQuizTarget.target;
                this.notifications.unshift(notification);
            }
        }
    }

    /**
     * Adds a notification about an updated exercise during a live exam to the component's state
     * and pushes updated problemStatement to student exam exercise via BehaviorSubjects
     *
     * @param notification {Notification}
     */
    private addExamUpdateNotification(notification: Notification): void {
        try {
            const target = JSON.parse(notification.target!);
            this.examExerciseUpdateService.updateLiveExamExercise(target.exercise, target.problemStatement);
        } catch (error) {
            this.alertService.error(error);
        }
        // only show pop-up if explicit notification text was set and only inside exam mode
        const matchOptions = { paths: 'exact', queryParams: 'exact', fragment: 'ignored', matrixParams: 'ignored' } as IsActiveMatchOptions; // corresponds to exact = true
        if (notification.text != undefined && this.router.isActive(this.notificationTargetRoute(notification), matchOptions)) {
            this.notifications.unshift(notification);
        }
    }

    /**
     * checks if the updated exercise, which notification is based on, is part of the student exam of this client
     * this might not be the case due to different/optional exerciseGroups
     * @param notification that hold information about the exercise like problemStatement or different ids
     */
    private checkIfNotificationAffectsCurrentStudentExamExercises(notification: Notification): void {
        if (!notification.target) {
            return;
        }
        const target = JSON.parse(notification.target);
        const exerciseId = target.exercise;

        if (!this.studentExamExerciseIds) {
            this.studentExamExerciseIds = this.examParticipationService.getExamExerciseIds();
            if (!this.studentExamExerciseIds) {
                // exercises were not loaded yet for current user -> exam update will be loaded when user starts/loads the exam
                return;
            }
        }

        const updatedExerciseIsPartOfStudentExam = this.studentExamExerciseIds?.find((exerciseIdentifier) => exerciseIdentifier === exerciseId);
        if (updatedExerciseIsPartOfStudentExam) {
            this.addExamUpdateNotification(notification);
            this.setRemovalTimeout(notification);
        }
    }

    private setRemovalTimeout(notification: Notification): void {
        setTimeout(() => {
            this.notifications = this.notifications.filter(({ id }) => id !== notification.id);
        }, 30000);
    }
}
