import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { BehaviorSubject, Observable, ReplaySubject, Subject, Subscription } from 'rxjs';
import dayjs from 'dayjs/esm';
import { map } from 'rxjs/operators';
import { createRequestOption } from 'app/shared/util/request.util';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { User } from 'app/core/user/user.model';
import { GroupNotification, GroupNotificationType } from 'app/entities/group-notification.model';
import {
    CONVERSATION_ADD_USER_CHANNEL_TITLE,
    CONVERSATION_ADD_USER_GROUP_CHAT_TITLE,
    CONVERSATION_CREATE_GROUP_CHAT_TITLE,
    CONVERSATION_CREATE_ONE_TO_ONE_CHAT_TITLE,
    CONVERSATION_REMOVE_USER_CHANNEL_TITLE,
    CONVERSATION_REMOVE_USER_GROUP_CHAT_TITLE,
    DATA_EXPORT_CREATED_TITLE,
    DATA_EXPORT_FAILED_TITLE,
    NEW_ANNOUNCEMENT_POST_TITLE,
    NEW_COURSE_POST_TITLE,
    NEW_EXERCISE_POST_TITLE,
    NEW_LECTURE_POST_TITLE,
    NEW_REPLY_FOR_COURSE_POST_TITLE,
    NEW_REPLY_FOR_EXERCISE_POST_TITLE,
    NEW_REPLY_FOR_LECTURE_POST_TITLE,
    Notification,
    QUIZ_EXERCISE_STARTED_TEXT,
    QUIZ_EXERCISE_STARTED_TITLE,
} from 'app/entities/notification.model';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { QuizExercise, QuizMode } from 'app/entities/quiz/quiz-exercise.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { RouteComponents } from 'app/shared/metis/metis.util';
import { convertDateFromServer } from 'app/utils/date.utils';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { NotificationSettingsService } from 'app/shared/user-settings/notification-settings/notification-settings.service';

const notificationsPerPage = 25;

@Injectable({ providedIn: 'root' })
export class NotificationService {
    public resourceUrl = 'api/notifications';
    notificationSubject: ReplaySubject<Notification[]>;
    singleNotificationSubject: Subject<Notification>;
    notifications: Notification[] = [];
    totalNotifications = 0;
    totalNotificationsSubject: ReplaySubject<number>;
    page = 0;
    loadingSubject: BehaviorSubject<boolean> = new BehaviorSubject<boolean>(false);

    initialized = false;
    loadTimeout: ReturnType<typeof setTimeout>;
    subscribedTopics: string[] = [];
    wsSubscriptions: Subscription[] = [];

    constructor(
        private jhiWebsocketService: JhiWebsocketService,
        private router: Router,
        private http: HttpClient,
        private accountService: AccountService,
        private activatedRoute: ActivatedRoute,
        private courseManagementService: CourseManagementService,
        private notificationSettingsService: NotificationSettingsService,
    ) {
        this.initNotificationObserver();

        this.notificationSettingsService.getNotificationSettingsUpdates().subscribe(() => {
            this.resetAndLoad();
        });

        this.accountService.getAuthenticationState().subscribe((user) => this.onUserIdentityChange(user));
    }

    private onUserIdentityChange(user: User | undefined) {
        if (user && !this.initialized) {
            this.subscribeToSingleUserNotificationUpdates(user);
            this.subscribeToTutorialGroupNotificationUpdates(user);
            this.subscribeToConversationNotificationUpdates(user);

            // Delay to prevent load if someone spam clicks the refresh button
            this.loadTimeout = setTimeout(() => {
                this.courseManagementService.getCoursesForNotifications().subscribe((courses) => {
                    if (courses && this.initialized) {
                        this.subscribeToGroupNotificationUpdates(courses);
                        this.subscribeToQuizUpdates(courses);
                    }
                });
                this.notificationSettingsService.refreshNotificationSettings();
            }, 15 * 1000);

            this.initialized = true;
        } else if (!user && this.initialized) {
            this.notifications = [];
            this.notificationSubject.next([]);
            this.page = 0;
            this.totalNotifications = 0;
            this.totalNotificationsSubject.next(0);
            clearTimeout(this.loadTimeout);

            this.subscribedTopics.forEach((topic) => this.jhiWebsocketService.unsubscribe(topic));
            this.wsSubscriptions.forEach((subscription) => subscription.unsubscribe());
            this.subscribedTopics = [];
            this.wsSubscriptions = [];

            this.initialized = false;
        }
    }

    private setTotalNotificationCount(newCount: number): void {
        this.totalNotifications = newCount;
        this.totalNotificationsSubject.next(newCount);
    }

    public incrementPageAndLoad(): void {
        // Avoid repeated calls as this is called on scroll
        if (this.loadingSubject.value) {
            return;
        }
        this.page += 1;
        this.loadNotifications();
    }

    public resetAndLoad(): void {
        this.page = 0;
        this.notifications = [];
        this.totalNotifications = 0;
        this.loadNotifications();
    }

    private loadNotifications(): void {
        if (this.totalNotifications === 0 || this.notifications.length < this.totalNotifications) {
            this.loadingSubject.next(true);
            this.queryNotificationsFilteredBySettings({
                page: this.page,
                size: notificationsPerPage,
                sort: ['notificationDate,desc'],
            }).subscribe({
                next: (res: HttpResponse<Notification[]>) => this.loadNotificationsSuccess(res.body!, res.headers),
            });
        }
    }

    private loadNotificationsSuccess(notifications: Notification[], headers: HttpHeaders): void {
        this.addNotifications(notifications, false);
        this.setTotalNotificationCount(Number(headers.get('X-Total-Count')!));
        this.loadingSubject.next(false);
    }

    private addNotification(notification: Notification): void {
        this.addNotifications([notification]);

        // Single notifications should also be sent through the single notification subject for the notifcation popup
        this.singleNotificationSubject.next(notification);
    }

    private addNotifications(notifications: Notification[], addToCount = true): void {
        if (notifications) {
            let countPushed = 0;
            notifications.forEach((notification: Notification) => {
                if (notification.notificationDate) {
                    notification.notificationDate = convertDateFromServer(notification.notificationDate);
                }

                if (
                    this.notificationSettingsService.isNotificationAllowedBySettings(notification) &&
                    !this.notifications.some(({ id }) => id === notification.id) &&
                    notification.notificationDate
                ) {
                    this.notifications.push(notification);
                    countPushed++;
                }
            });

            this.notificationSubject.next(notifications);
            if (addToCount) {
                this.setTotalNotificationCount(this.totalNotifications + countPushed);
            }
        }
    }

    /**
     * Query all notifications with respect to the current user's notification settings.
     * @param req request options
     * @return Observable<HttpResponse<Notification[]>>
     */
    queryNotificationsFilteredBySettings(req?: any): Observable<HttpResponse<Notification[]>> {
        const options = createRequestOption(req);
        return this.http
            .get<Notification[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: HttpResponse<Notification[]>) => this.convertNotificationResponseArrayDateFromServer(res)));
    }

    /**
     * Navigate to notification target or build router components and params for post related notifications
     * @param {GroupNotification} notification
     */
    interpretNotification(notification: GroupNotification): void {
        if (notification.target) {
            const target = JSON.parse(notification.target);
            const targetCourseId = target.course || notification.course?.id;
            const targetConversationId = target.conversation;

            if (notification.title === DATA_EXPORT_CREATED_TITLE) {
                this.router.navigate([target.mainPage, 'data-exports', target.id]);
            } else if (notification.title === DATA_EXPORT_FAILED_TITLE) {
                this.router.navigate([target.mainPage, 'data-exports']);
            } else if (notification.title === QUIZ_EXERCISE_STARTED_TITLE) {
                this.router.navigate([target.mainPage, targetCourseId, 'quiz-exercises', target.id, 'live']);
            } else if (
                // check with plain strings is needed to support legacy notifications that were created before it was possible to translate notifications
                notification.title === NEW_ANNOUNCEMENT_POST_TITLE ||
                notification.title === 'New announcement' ||
                notification.title === NEW_COURSE_POST_TITLE ||
                notification.title === 'New course-wide post' ||
                notification.title === NEW_REPLY_FOR_COURSE_POST_TITLE ||
                notification.title === 'New reply for course-wide post'
            ) {
                const queryParams: Params = MetisService.getQueryParamsForCoursePost(target.id);
                const routeComponents: RouteComponents = MetisService.getLinkForCoursePost(targetCourseId);
                this.navigateToNotificationTarget(targetCourseId, routeComponents, queryParams);
            } else if (
                notification.title === NEW_EXERCISE_POST_TITLE ||
                notification.title === 'New exercise post' ||
                notification.title === NEW_REPLY_FOR_EXERCISE_POST_TITLE ||
                notification.title === 'New reply for exercise post'
            ) {
                const queryParams: Params = MetisService.getQueryParamsForLectureOrExercisePost(target.id);
                const routeComponents: RouteComponents = MetisService.getLinkForExercisePost(targetCourseId, target.exercise ?? target.exerciseId);
                this.navigateToNotificationTarget(targetCourseId, routeComponents, queryParams);
            } else if (
                notification.title === NEW_LECTURE_POST_TITLE ||
                notification.title === 'New lecture post' ||
                notification.title === NEW_REPLY_FOR_LECTURE_POST_TITLE ||
                notification.title === 'New reply for lecture post'
            ) {
                const queryParams: Params = MetisService.getQueryParamsForLectureOrExercisePost(target.id);
                const routeComponents: RouteComponents = MetisService.getLinkForLecturePost(targetCourseId, target.lecture ?? target.lectureId);
                this.navigateToNotificationTarget(targetCourseId, routeComponents, queryParams);
            } else if (
                notification.title === CONVERSATION_CREATE_GROUP_CHAT_TITLE ||
                notification.title === CONVERSATION_ADD_USER_CHANNEL_TITLE ||
                notification.title === CONVERSATION_ADD_USER_GROUP_CHAT_TITLE ||
                notification.title === CONVERSATION_REMOVE_USER_GROUP_CHAT_TITLE ||
                notification.title === CONVERSATION_REMOVE_USER_CHANNEL_TITLE
            ) {
                const queryParams: Params = MetisConversationService.getQueryParamsForConversation(targetConversationId);
                const routeComponents: RouteComponents = MetisConversationService.getLinkForConversation(targetCourseId);
                this.navigateToNotificationTarget(targetCourseId, routeComponents, queryParams);
            } else {
                const routeComponents: RouteComponents = [target.mainPage, targetCourseId, target.entity, target.id];
                this.navigateToNotificationTarget(targetCourseId, routeComponents, {});
            }
        }
    }

    /**
     * Navigate to post related targets, decide if reload is required, i.e. when switching course context
     * @param {number} targetCourseId
     * @param {RouteComponents} routeComponents
     * @param {Params} queryParams
     */
    navigateToNotificationTarget(targetCourseId: number, routeComponents: RouteComponents, queryParams: Params): void {
        const currentCourseId = this.getCurrentCourseId();
        // determine if component recreation is required when notification is clicked
        // by comparing the id of the course the user is currently in, the course the post associated with the notification belongs to and if the user is already in the messages tab
        if (currentCourseId === undefined || currentCourseId !== targetCourseId || this.isUnderMessagesTabOfSpecificCourse(targetCourseId.toString())) {
            this.forceComponentReload(routeComponents, queryParams);
        } else {
            this.router.navigate(routeComponents, { queryParams });
        }
    }

    private getCurrentCourseId(): number | undefined {
        return this.activatedRoute.snapshot.firstChild?.params['courseId'];
    }

    /**
     * Force component reload. This is used when the user clicks on a notification and the component needs to be recreated.
     * @param routeComponents
     * @param queryParams
     */
    forceComponentReload(routeComponents: RouteComponents, queryParams: Params): void {
        this.router.navigate(['/courses'], { skipLocationChange: true }).then(() => {
            this.router.navigate(routeComponents, { queryParams });
        });
    }

    subscribeToNotificationUpdates(): Observable<Notification[]> {
        return this.notificationSubject.asObservable();
    }

    subscribeToSingleIncomingNotifications(): Observable<Notification> {
        return this.singleNotificationSubject.asObservable();
    }

    subscribeToLoadingStateUpdates(): Observable<boolean> {
        return this.loadingSubject.asObservable();
    }

    subscribeToTotalNotificationCountUpdates(): Observable<number> {
        return this.totalNotificationsSubject.asObservable();
    }

    private subscribeToSingleUserNotificationUpdates(user: User): void {
        const userTopic = `/topic/user/${user.id}/notifications`;
        if (!this.subscribedTopics.includes(userTopic)) {
            this.subscribedTopics.push(userTopic);
            this.jhiWebsocketService.subscribe(userTopic);
            const subscription = this.jhiWebsocketService.receive(userTopic).subscribe((notification: Notification) => {
                // Do not add notification to observer if it is a one-to-one conversation creation notification
                // and if the author is the current user
                if (notification.title !== CONVERSATION_CREATE_ONE_TO_ONE_CHAT_TITLE && user.id !== notification.author?.id) {
                    this.addNotification(notification);
                }
            });
            this.wsSubscriptions.push(subscription);
        }
    }

    /**
     * Check if user is under messages tab.
     * @returns {boolean} true if user is under messages tab, false otherwise
     */
    private isUnderMessagesTabOfSpecificCourse(targetCourseId: string): boolean {
        return this.router.url.includes(`courses/${targetCourseId}/messages`);
    }

    private subscribeToGroupNotificationUpdates(courses: Course[]): void {
        courses.forEach((course) => {
            let courseTopic = `/topic/course/${course.id}/${GroupNotificationType.STUDENT}`;
            if (course.isAtLeastInstructor) {
                courseTopic = `/topic/course/${course.id}/${GroupNotificationType.INSTRUCTOR}`;
            } else if (course.isAtLeastEditor) {
                courseTopic = `/topic/course/${course.id}/${GroupNotificationType.EDITOR}`;
            } else if (course.isAtLeastTutor) {
                courseTopic = `/topic/course/${course.id}/${GroupNotificationType.TA}`;
            }
            if (!this.subscribedTopics.includes(courseTopic)) {
                this.subscribedTopics.push(courseTopic);
                this.jhiWebsocketService.subscribe(courseTopic);
                const subscription = this.jhiWebsocketService.receive(courseTopic).subscribe((notification: Notification) => {
                    this.addNotification(notification);
                });
                this.wsSubscriptions.push(subscription);
            }
        });
    }

    private subscribeToTutorialGroupNotificationUpdates(user: User): void {
        const tutorialGroupTopic = `/topic/user/${user.id}/notifications/tutorial-groups`;
        if (!this.subscribedTopics.includes(tutorialGroupTopic)) {
            this.subscribedTopics.push(tutorialGroupTopic);
            this.jhiWebsocketService.subscribe(tutorialGroupTopic);
            const subscription = this.jhiWebsocketService.receive(tutorialGroupTopic).subscribe((notification: Notification) => {
                this.addNotification(notification);
            });
            this.wsSubscriptions.push(subscription);
        }
    }

    private subscribeToConversationNotificationUpdates(user: User): void {
        const conversationTopic = `/topic/user/${user.id}/notifications/conversations`;
        if (!this.subscribedTopics.includes(conversationTopic)) {
            this.subscribedTopics.push(conversationTopic);
            this.jhiWebsocketService.subscribe(conversationTopic);
            const subscription = this.jhiWebsocketService.receive(conversationTopic).subscribe((notification: Notification) => {
                if (notification.target) {
                    const target = JSON.parse(notification.target);
                    const targetCourseId = target.course;
                    // Only add notification if it is not from the current user and the user is not already in the messages tab
                    if (notification.author?.id !== this.accountService.userIdentity?.id && !this.isUnderMessagesTabOfSpecificCourse(targetCourseId)) {
                        this.addNotification(notification);
                    }
                }
            });
            this.wsSubscriptions.push(subscription);
        }
    }

    private subscribeToQuizUpdates(courses: Course[]): void {
        courses.forEach((course) => {
            const quizExerciseTopic = '/topic/courses/' + course.id + '/quizExercises';
            if (!this.subscribedTopics.includes(quizExerciseTopic)) {
                this.subscribedTopics.push(quizExerciseTopic);
                this.jhiWebsocketService.subscribe(quizExerciseTopic);
                const subscription = this.jhiWebsocketService.receive(quizExerciseTopic).subscribe((quizExercise: QuizExercise) => {
                    if (
                        quizExercise.visibleToStudents &&
                        quizExercise.quizMode === QuizMode.SYNCHRONIZED &&
                        quizExercise.quizBatches?.[0]?.started &&
                        !quizExercise.isOpenForPractice
                    ) {
                        this.addNotification(NotificationService.createNotificationFromStartedQuizExercise(quizExercise));
                    }
                });
                this.wsSubscriptions.push(subscription);
            }
        });
    }

    private static createNotificationFromStartedQuizExercise(quizExercise: QuizExercise): GroupNotification {
        return {
            title: QUIZ_EXERCISE_STARTED_TITLE,
            text: QUIZ_EXERCISE_STARTED_TEXT,
            textIsPlaceholder: true,
            placeholderValues: '["' + quizExercise.course!.title + '","' + quizExercise.title + '"]',
            notificationDate: dayjs(),
            target: JSON.stringify({
                course: quizExercise.course!.id,
                mainPage: 'courses',
                entity: 'exercises',
                id: quizExercise.id,
            }),
        } as GroupNotification;
    }

    private convertNotificationResponseArrayDateFromServer(res: HttpResponse<Notification[]>): HttpResponse<Notification[]> {
        if (res.body) {
            res.body.forEach((notification: Notification) => {
                notification.notificationDate = convertDateFromServer(notification.notificationDate);
            });
        }
        return res;
    }

    /**
     * Set new notification observer.
     */
    private initNotificationObserver(): void {
        this.notificationSubject = new ReplaySubject<Notification[]>(1);
        this.totalNotificationsSubject = new ReplaySubject<number>(1);
        this.singleNotificationSubject = new Subject<Notification>();
    }
}
