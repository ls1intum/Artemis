import { Component, OnInit } from '@angular/core';
import { Router, UrlTree } from '@angular/router';
import { NotificationService } from 'app/shared/notification/notification.service';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Notification } from 'app/entities/notification.model';
import { GroupNotification } from 'app/entities/group-notification.model';
import { LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE } from 'app/shared/notification/notification.constants';
import { ExamExerciseUpdateService } from 'app/exam/manage/exam-exercise-update.service';
import { JhiAlertService } from 'ng-jhipster';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { StudentExam } from 'app/entities/student-exam.model';

@Component({
    selector: 'jhi-notification-popup',
    templateUrl: './notification-popup.component.html',
    styleUrls: ['./notification-popup.scss'],
})
export class NotificationPopupComponent implements OnInit {
    notifications: Notification[] = [];

    LiveExamExerciseUpdateNotificationTitleHtmlConst = LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE;

    constructor(
        private accountService: AccountService,
        private notificationService: NotificationService,
        private router: Router,
        private examExerciseUpdateService: ExamExerciseUpdateService,
        private jhiAlertService: JhiAlertService,
        private examParticipationService: ExamParticipationService,
    ) {}

    /**
     * Subscribe to notification updates that are received via websocket if the user is logged in.
     */
    ngOnInit(): void {
        this.accountService.getAuthenticationState().subscribe((user: User | undefined) => {
            if (user) {
                this.subscribeToNotificationUpdates();
            }
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
        if (notification.title === LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE) {
            const target = JSON.parse(notification.target!);
            this.examExerciseUpdateService.navigateToExamExercise(target.exercise);
        } else {
            this.router.navigateByUrl(this.notificationTargetRoute(notification));
        }
    }

    private notificationTargetRoute(notification: Notification): UrlTree | string {
        if (notification.target) {
            const target = JSON.parse(notification.target);
            if (notification.title === LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE) {
                return this.router.createUrlTree([target.mainPage, target.course, target.entity, target.exam]);
            } else if (notification.title === 'Quiz started' && target.status) {
                return this.router.createUrlTree([target.mainPage, target.course, target.entity, target.id, target.status]);
            } else {
                return this.router.createUrlTree([target.mainPage, target.course, target.entity, target.id]);
            }
        }
        return this.router.url;
    }

    private subscribeToNotificationUpdates(): void {
        this.notificationService.subscribeToNotificationUpdates().subscribe((notification: Notification) => {
            this.addNotification(notification);
        });
    }

    private addNotification(notification: Notification): void {
        // Only add a notification if it does not already exist.
        if (notification && !this.notifications.some(({ id }) => id === notification.id)) {
            if (notification.title === 'Quiz started') {
                this.addQuizNotification(notification);
                this.setRemovalTimeout(notification);
            }
            if (notification.title === LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE) {
                this.checkIfNotificationAffectsCurrentStudentExamExercises(notification);
            }
        }
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
            if (
                !this.router.isActive(this.notificationTargetRoute(notification), true) &&
                !this.router.isActive(this.notificationTargetRoute(notificationWithLiveQuizTarget) + '/live', true)
            ) {
                notification.target = notificationWithLiveQuizTarget.target;
                this.notifications.unshift(notification);
            }
        }
    }

    /**
     * Adds a notification about a updated exercise during a live exam to the component's state
     * and pushes updated problemStatement to student exam exercise via BehaviorSubjects
     *
     * @param notification {Notification}
     */
    private addExamUpdateNotification(notification: Notification): void {
        try {
            const target = JSON.parse(notification.target!);
            this.examExerciseUpdateService.updateLiveExamExercise(target.exercise, target.problemStatement);
        } catch (error) {
            this.jhiAlertService.error(error);
        }
        // only show pop-up if explicit notification text was set and only inside exam mode
        if (notification.text != undefined && this.router.isActive(this.notificationTargetRoute(notification), true)) {
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
        const courseId = target.course;
        const examId = target.exam;
        const exerciseId = target.exercise;
        this.examParticipationService.loadStudentExamWithExercisesForSummary(courseId, examId).subscribe((studentExamWithExercises: StudentExam) => {
            const updatedExerciseIsPartOfStudentExam = studentExamWithExercises?.exercises?.find((studentExamExercise) => studentExamExercise.id === exerciseId);
            if (updatedExerciseIsPartOfStudentExam != undefined) {
                this.addExamUpdateNotification(notification);
                this.setRemovalTimeout(notification);
            }
        });
    }

    private setRemovalTimeout(notification: Notification): void {
        setTimeout(() => {
            this.notifications = this.notifications.filter(({ id }) => id !== notification.id);
        }, 30000);
    }
}
