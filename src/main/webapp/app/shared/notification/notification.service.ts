import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import * as moment from 'moment';
import { map } from 'rxjs/operators';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared/util/request-util';
import { Router } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { User } from 'app/core/user/user.model';
import { GroupNotification, GroupNotificationType } from 'app/entities/group-notification.model';
import { Notification } from 'app/entities/notification.model';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

@Injectable({ providedIn: 'root' })
export class NotificationService {
    public resourceUrl = SERVER_API_URL + 'api/notifications';
    notificationObserver: BehaviorSubject<Notification | null>;
    notificationObserverNEW: BehaviorSubject<Notification | null>; // TODO: replace existing notificationObserver
    subscribedTopics: string[] = [];
    cachedNotifications: Observable<HttpResponse<Notification[]>>;

    constructor(
        private jhiWebsocketService: JhiWebsocketService,
        private router: Router,
        private http: HttpClient,
        private accountService: AccountService,
        private courseManagementService: CourseManagementService,
    ) {
        this.initNotificationObserver();
    }

    /**
     * Query all notifications.
     * @param req request options
     * @return Observable<HttpResponse<Notification[]>>
     */
    query(req?: any): Observable<HttpResponse<Notification[]>> {
        const options = createRequestOption(req);
        return this.http
            .get<Notification[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: HttpResponse<Notification[]>) => this.convertDateArrayFromServer(res)));
    }

    /**
     * Delete notification by id.
     * @param {number} id
     * @return Observable<HttpResponse<any>>
     */
    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    /**
     * TODO: remove
     * Subscribe to notifications for user.
     * @return Promise<any>
     */
    subscribeUserNotifications(): Promise<any> {
        return new Promise((resolve, reject) => {
            this.accountService
                .identity()
                .then((user: User) => {
                    if (user) {
                        const userTopic = `/topic/user/${user.id}/notifications`;
                        if (!this.subscribedTopics.includes(userTopic)) {
                            this.subscribedTopics.push(userTopic);
                            this.jhiWebsocketService.subscribe(userTopic);
                            this.jhiWebsocketService.receive(userTopic).subscribe((notification: Notification) => {
                                this.notificationObserver.next(notification);
                            });
                            resolve();
                        }
                    } else {
                        reject('no User');
                    }
                })
                .catch((error) => reject(error));
        });
    }

    /**
     * TODO: remove
     * Get the notificationObserver.
     * @return {BehaviorSubject<Notification}
     */
    subscribeToSocketMessages(): BehaviorSubject<Notification | null> {
        return this.notificationObserver;
    }

    /**
     * Navigate to notification target.
     * @param {GroupNotification} notification
     */
    interpretNotification(notification: GroupNotification): void {
        const target = JSON.parse(notification.target);
        const courseId = target.course || notification.course.id;
        this.router.navigate([target.mainPage, courseId, target.entity, target.id]);
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
     * @returns {BehaviorSubject<Notification | null>}
     */
    subscribeToNotificationUpdates(): BehaviorSubject<Notification | null> {
        this.subscribeToSingleUserNotificationUpdates();
        this.courseManagementService.getCoursesForNotifications().subscribe((courses) => {
            if (courses) {
                this.subscribeToGroupNotificationUpdates(courses);
                this.subscribeToQuizUpdates(courses);
            }
        });
        return this.notificationObserverNEW;
    }

    private subscribeToSingleUserNotificationUpdates(): void {
        this.accountService.identity().then((user) => {
            if (user) {
                const userTopic = `/topic/user/${user.id}/notifications`;
                if (!this.subscribedTopics.includes(userTopic)) {
                    this.subscribedTopics.push(userTopic);
                    this.jhiWebsocketService.subscribe(userTopic);
                    this.jhiWebsocketService.receive(userTopic).subscribe((notification: Notification) => {
                        this.notificationObserverNEW.next(notification);
                    });
                }
            }
        });
    }

    private subscribeToGroupNotificationUpdates(courses: Course[]): void {
        courses.forEach((course) => {
            let courseTopic = `/topic/course/${course.id}/${GroupNotificationType.STUDENT}`;
            if (this.accountService.isAtLeastInstructorInCourse(course)) {
                courseTopic = `/topic/course/${course.id}/${GroupNotificationType.INSTRUCTOR}`;
            } else if (this.accountService.isAtLeastTutorInCourse(course)) {
                courseTopic = `/topic/course/${course.id}/${GroupNotificationType.TA}`;
            }
            if (!this.subscribedTopics.includes(courseTopic)) {
                this.subscribedTopics.push(courseTopic);
                this.jhiWebsocketService.subscribe(courseTopic);
                this.jhiWebsocketService.receive(courseTopic).subscribe((notification: Notification) => {
                    this.notificationObserverNEW.next(notification);
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
                    // TODO: enhance condition.
                    if (quizExercise.started) {
                        this.createNotificationFromStartedQuizExercise(quizExercise);
                    }
                });
            }
        });
    }

    private createNotificationFromStartedQuizExercise(quizExercise: QuizExercise) {
        // TODO: use the quizExercise to create the notification text and target
        const notification = {
            title: 'Quiz started',
            text: 'Quiz "TODO" just started.',
        } as GroupNotification;
        this.notificationObserverNEW.next(notification);
    }

    private convertDateArrayFromServer(res: HttpResponse<Notification[]>): HttpResponse<Notification[]> {
        if (res.body) {
            res.body.forEach((notification: Notification) => {
                notification.notificationDate = notification.notificationDate != null ? moment(notification.notificationDate) : null;
            });
        }
        return res;
    }

    /**
     * Set new notification observer.
     */
    private initNotificationObserver(): void {
        this.notificationObserver = new BehaviorSubject<Notification | null>(null);
        this.notificationObserverNEW = new BehaviorSubject<Notification | null>(null);
    }
}
