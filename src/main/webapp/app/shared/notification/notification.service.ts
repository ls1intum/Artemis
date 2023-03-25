import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';
import { CourseConversationsNotificationsService } from 'app/overview/course-conversations-notifications-service';
import { Observable, ReplaySubject } from 'rxjs';
import dayjs from 'dayjs/esm';
import { map } from 'rxjs/operators';

import { createRequestOption } from 'app/shared/util/request.util';
import { ActivatedRoute, Params, Router, UrlSerializer } from '@angular/router';
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
    NEW_ANNOUNCEMENT_POST_TITLE,
    NEW_COURSE_POST_TITLE,
    NEW_EXERCISE_POST_TITLE,
    NEW_LECTURE_POST_TITLE,
    NEW_REPLY_FOR_COURSE_POST_TITLE,
    NEW_REPLY_FOR_EXERCISE_POST_TITLE,
    NEW_REPLY_FOR_LECTURE_POST_TITLE,
    Notification,
} from 'app/entities/notification.model';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { QuizExercise, QuizMode } from 'app/entities/quiz/quiz-exercise.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { RouteComponents } from 'app/shared/metis/metis.util';
import { convertDateFromServer } from 'app/utils/date.utils';
import { TutorialGroupsNotificationService } from 'app/course/tutorial-groups/services/tutorial-groups-notification.service';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';

@Injectable({ providedIn: 'root' })
export class NotificationService {
    public resourceUrl = SERVER_API_URL + 'api/notifications';
    subscribedTopics: string[] = [];
    notificationObserver: ReplaySubject<Notification>;
    cachedNotifications: Observable<HttpResponse<Notification[]>>;

    constructor(
        private jhiWebsocketService: JhiWebsocketService,
        private router: Router,
        private http: HttpClient,
        private accountService: AccountService,
        private activatedRoute: ActivatedRoute,
        private courseManagementService: CourseManagementService,
        private serializer: UrlSerializer,
        private tutorialGroupsNotificationService: TutorialGroupsNotificationService,
        private courseConversationsNotificationsService: CourseConversationsNotificationsService,
    ) {
        this.initNotificationObserver();
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

            if (notification.title === 'Quiz started') {
                this.router.navigate([target.mainPage, targetCourseId, 'quiz-exercises', target.id, 'live']);
            } else if (
                notification.title === NEW_ANNOUNCEMENT_POST_TITLE ||
                notification.title === NEW_COURSE_POST_TITLE ||
                notification.title === NEW_REPLY_FOR_COURSE_POST_TITLE
            ) {
                const queryParams: Params = MetisService.getQueryParamsForCoursePost(target.id);
                const routeComponents: RouteComponents = MetisService.getLinkForCoursePost(targetCourseId);
                this.navigateToNotificationTarget(targetCourseId, routeComponents, queryParams);
            } else if (notification.title === NEW_EXERCISE_POST_TITLE || notification.title === NEW_REPLY_FOR_EXERCISE_POST_TITLE) {
                const queryParams: Params = MetisService.getQueryParamsForLectureOrExercisePost(target.id);
                const routeComponents: RouteComponents = MetisService.getLinkForExercisePost(targetCourseId, target.exercise ?? target.exerciseId);
                this.navigateToNotificationTarget(targetCourseId, routeComponents, queryParams);
            } else if (notification.title === NEW_LECTURE_POST_TITLE || notification.title === NEW_REPLY_FOR_LECTURE_POST_TITLE) {
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

    /**
     * Init new observer for notifications and reset topics.
     */
    cleanUp(): void {
        this.cachedNotifications = new Observable<HttpResponse<Notification[]>>();
        this.initNotificationObserver();
        this.subscribedTopics = [];
    }

    /**
     * Subscribe to single user notification, group notification and quiz updates if it was not already subscribed.
     * Then it returns a BehaviorSubject the calling component can listen on to actually receive the notifications.
     * @returns {ReplaySubject<Notification>}
     */
    subscribeToNotificationUpdates(): ReplaySubject<Notification> {
        this.subscribeToSingleUserNotificationUpdates();
        this.courseManagementService.getCoursesForNotifications().subscribe((courses) => {
            if (courses) {
                this.subscribeToGroupNotificationUpdates(courses);
                this.subscribeToQuizUpdates(courses);
            }
        });
        this.tutorialGroupsNotificationService.getTutorialGroupsForNotifications().subscribe((tutorialGroups) => {
            if (tutorialGroups) {
                this.subscribeToTutorialGroupNotificationUpdates(tutorialGroups);
            }
        });
        this.courseConversationsNotificationsService.getConversationsForNotifications().subscribe((conversations) => {
            if (conversations) {
                this.subscribeToConversationNotificationUpdates(conversations);
            }
        });
        return this.notificationObserver;
    }

    private subscribeToSingleUserNotificationUpdates(): void {
        this.accountService.identity().then((user: User | undefined) => {
            if (user) {
                const userTopic = `/topic/user/${user.id}/notifications`;
                if (!this.subscribedTopics.includes(userTopic)) {
                    this.subscribedTopics.push(userTopic);
                    this.jhiWebsocketService.subscribe(userTopic);
                    this.jhiWebsocketService.receive(userTopic).subscribe((notification: Notification) => {
                        // Do not add notification to observer if it is a one-to-one conversation creation notification
                        // and if the author is the current user
                        if (notification.title !== CONVERSATION_CREATE_ONE_TO_ONE_CHAT_TITLE && user.id !== notification.author?.id) {
                            this.addNotificationToObserver(notification);
                        }
                        if (notification.target) {
                            const target = JSON.parse(notification.target);
                            const message = target.message;

                            // subscribe to newly created conversation topic
                            if (message === 'conversation-creation') {
                                const conversationId = target.conversation;
                                const conversationTopic = '/topic/conversation/' + conversationId + '/notifications';
                                this.subscribeToNewlyCreatedConversation(conversationTopic);
                            }

                            // unsubscribe from deleted conversation topic
                            if (message === 'conversation-deletion') {
                                const conversationId = target.conversation;
                                const conversationTopic = '/topic/conversation/' + conversationId + '/notifications';
                                this.unsubscribeFromDeletedConversation(conversationTopic);
                            }
                        }
                    });
                }
            }
        });
    }

    /**
     * Check if user is under messages tab.
     * @returns {boolean} true if user is under messages tab, false otherwise
     */
    private isUnderMessagesTabOfSpecificCourse(targetCourseId: string): boolean {
        return this.router.url.includes(`courses/${targetCourseId}/messages`);
    }

    /**
     * Unsubscribe from deleted conversation topic (e.g. when user deletes a conversation or when user is removed from conversation)
     */
    private unsubscribeFromDeletedConversation(conversationTopic: string): void {
        this.jhiWebsocketService.unsubscribe(conversationTopic);
        this.subscribedTopics = this.subscribedTopics.filter((topic) => topic !== conversationTopic);
    }

    /**
     * Subscribe to newly created conversation topic (e.g. when user is added to a new conversation)
     */
    private subscribeToNewlyCreatedConversation(conversationTopic: string): void {
        this.subscribedTopics.push(conversationTopic);
        this.jhiWebsocketService.subscribe(conversationTopic);
        this.jhiWebsocketService.receive(conversationTopic).subscribe((notification: Notification) => {
            if (notification.target) {
                const target = JSON.parse(notification.target);
                const targetCourseId = target.course;
                // Do not add if under messages tab of specific course
                if (!this.isUnderMessagesTabOfSpecificCourse(targetCourseId)) {
                    this.addNotificationToObserver(notification);
                }
            }
        });
    }

    private subscribeToGroupNotificationUpdates(courses: Course[]): void {
        courses.forEach((course) => {
            let courseTopic = `/topic/course/${course.id}/${GroupNotificationType.STUDENT}`;
            if (this.accountService.isAtLeastInstructorInCourse(course)) {
                courseTopic = `/topic/course/${course.id}/${GroupNotificationType.INSTRUCTOR}`;
            } else if (this.accountService.isAtLeastEditorInCourse(course)) {
                courseTopic = `/topic/course/${course.id}/${GroupNotificationType.EDITOR}`;
            } else if (this.accountService.isAtLeastTutorInCourse(course)) {
                courseTopic = `/topic/course/${course.id}/${GroupNotificationType.TA}`;
            }
            if (!this.subscribedTopics.includes(courseTopic)) {
                this.subscribedTopics.push(courseTopic);
                this.jhiWebsocketService.subscribe(courseTopic);
                this.jhiWebsocketService.receive(courseTopic).subscribe((notification: Notification) => {
                    this.addNotificationToObserver(notification);
                });
            }
        });
    }

    private subscribeToTutorialGroupNotificationUpdates(tutorialGroups: TutorialGroup[]): void {
        tutorialGroups.forEach((tutorialGroup) => {
            const tutorialGroupTopic = '/topic/tutorial-group/' + tutorialGroup.id + '/notifications';
            if (!this.subscribedTopics.includes(tutorialGroupTopic)) {
                this.subscribedTopics.push(tutorialGroupTopic);
                this.jhiWebsocketService.subscribe(tutorialGroupTopic);
                this.jhiWebsocketService.receive(tutorialGroupTopic).subscribe((notification: Notification) => {
                    this.addNotificationToObserver(notification);
                });
            }
        });
    }

    private subscribeToConversationNotificationUpdates(conversations: Conversation[]): void {
        conversations.forEach((conversation) => {
            const conversationTopic = '/topic/conversation/' + conversation.id + '/notifications';
            if (!this.subscribedTopics.includes(conversationTopic)) {
                this.subscribedTopics.push(conversationTopic);
                this.jhiWebsocketService.subscribe(conversationTopic);
                this.jhiWebsocketService.receive(conversationTopic).subscribe((notification: Notification) => {
                    if (notification.target) {
                        const target = JSON.parse(notification.target);
                        const targetCourseId = target.course;
                        // Only add notification if it is not from the current user and the user is not already in the messages tab
                        if (notification.author?.id !== this.accountService.userIdentity?.id && !this.isUnderMessagesTabOfSpecificCourse(targetCourseId)) {
                            this.addNotificationToObserver(notification);
                        }
                    }
                });
            }
        });
    }

    private subscribeToQuizUpdates(courses: Course[]): void {
        courses.forEach((course) => {
            const quizExerciseTopic = '/topic/courses/' + course.id + '/quizExercises';
            if (!this.subscribedTopics.includes(quizExerciseTopic)) {
                this.subscribedTopics.push(quizExerciseTopic);
                this.jhiWebsocketService.subscribe(quizExerciseTopic);
                this.jhiWebsocketService.receive(quizExerciseTopic).subscribe((quizExercise: QuizExercise) => {
                    if (
                        quizExercise.visibleToStudents &&
                        quizExercise.quizMode === QuizMode.SYNCHRONIZED &&
                        quizExercise.quizBatches?.[0]?.started &&
                        !quizExercise.isOpenForPractice
                    ) {
                        this.addNotificationToObserver(NotificationService.createNotificationFromStartedQuizExercise(quizExercise));
                    }
                });
            }
        });
    }

    private static createNotificationFromStartedQuizExercise(quizExercise: QuizExercise): GroupNotification {
        return {
            title: 'Quiz started',
            text: 'Quiz "' + quizExercise.title + '" just started.',
            notificationDate: dayjs(),
            target: JSON.stringify({
                course: quizExercise.course!.id,
                mainPage: 'courses',
                entity: 'exercises',
                id: quizExercise.id,
            }),
        } as GroupNotification;
    }

    private addNotificationToObserver(notification: Notification): void {
        if (notification && notification.notificationDate) {
            notification.notificationDate = dayjs(notification.notificationDate);
            this.notificationObserver.next(notification);
        }
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
        this.notificationObserver = new ReplaySubject<Notification>();
    }
}
