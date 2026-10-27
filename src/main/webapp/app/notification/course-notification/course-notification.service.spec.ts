import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { CourseNotificationService } from 'app/notification/course-notification/course-notification.service';
import { CourseNotification } from 'app/notification/shared/entities/course-notification/course-notification';
import { CourseNotificationViewingStatus } from 'app/notification/shared/entities/course-notification/course-notification-viewing-status';
import { CourseNotificationPage } from 'app/openapi/model/course-notification-page';
import { NewPostCourseNotification } from 'app/openapi/model/new-post-course-notification';
import { CourseNotificationInfo } from 'app/openapi/model/course-notification-info';
import { UserCourseNotificationStatusUpdateRequest } from 'app/openapi/model/user-course-notification-status-update-request';
import { CourseNotificationCategory } from 'app/notification/shared/entities/course-notification/course-notification-category';
import { CourseNotificationChannel } from 'app/notification/shared/entities/course-notification/course-notification-channel';
import dayjs from 'dayjs/esm';
import { faComments, faWandMagicSparkles } from '@fortawesome/free-solid-svg-icons';
import { BehaviorSubject, distinctUntilChanged, firstValueFrom } from 'rxjs';
import { User } from 'app/account/user/user.model';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('CourseNotificationService', () => {
    let service: CourseNotificationService;
    let httpMock: HttpTestingController;

    const wireNotification = (notificationId: number, courseId = 123): NewPostCourseNotification => ({
        notificationType: 'newPostNotification',
        notificationId,
        courseId,
        category: 'COMMUNICATION',
        status: 'UNSEEN',
        creationDate: '2026-10-25T02:30:00+02:00',
        courseTitle: 'Java Programming',
        courseIconUrl: 'icon.svg',
        payload: { postId: 99, channelId: 20, authorIsBot: false },
        relativeWebAppUrl: '/courses/123/communication',
    });
    const wirePage = (content: CourseNotificationPage['content'], totalPages = 1): CourseNotificationPage => ({
        content,
        pageNumber: 0,
        pageSize: 10,
        totalElements: content?.length ?? 0,
        totalPages,
    });

    beforeEach(() => {
        vi.useFakeTimers();
        TestBed.configureTestingModule({
            providers: [CourseNotificationService, provideHttpClient(), provideHttpClientTesting(), { provide: AccountService, useClass: MockAccountService }],
        });

        service = TestBed.inject(CourseNotificationService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        vi.useRealTimers();
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('getInfo generated body and cache', () => {
        const info: CourseNotificationInfo = { notificationTypes: { '1': 'newPostNotification' }, channels: ['WEBAPP', 'PUSH', 'EMAIL'], presets: [] };

        it('returns the body directly and reuses only its successful cached value', () => {
            let first: CourseNotificationInfo | undefined;
            let cached: CourseNotificationInfo | undefined;
            service.getInfo().subscribe((body) => (first = body));
            const request = httpMock.expectOne('/api/notification/courses/info');
            expect(request.request.method).toBe('GET');
            request.flush(info);
            service.getInfo().subscribe((body) => (cached = body));
            httpMock.expectNone('/api/notification/courses/info');
            expect(first).toEqual(info);
            expect(cached).toBe(first);
        });

        it('does not poison the cache on an error and retries the generated request', () => {
            let error: HttpErrorResponse | undefined;
            service.getInfo().subscribe({ error: (value: HttpErrorResponse) => (error = value) });
            httpMock.expectOne('/api/notification/courses/info').flush({}, { status: 503, statusText: 'Unavailable' });
            expect(error?.status).toBe(503);
            expect(service['cachedNotificationInfo']).toBeNull();
            let result: CourseNotificationInfo | undefined;
            service.getInfo().subscribe((body) => (result = body));
            httpMock.expectOne('/api/notification/courses/info').flush(info);
            expect(result).toEqual(info);
        });

        it('keeps the existing independent in-flight request behavior', () => {
            service.getInfo().subscribe();
            service.getInfo().subscribe();
            const requests = httpMock.match('/api/notification/courses/info');
            expect(requests).toHaveLength(2);
            requests.forEach((request) => request.flush(info));
        });

        it('does not cache a null successful body', () => {
            service.getInfo().subscribe();
            httpMock.expectOne('/api/notification/courses/info').flush(null);
            expect(service['cachedNotificationInfo']).toBeNull();
            service.getInfo().subscribe();
            httpMock.expectOne('/api/notification/courses/info').flush(info);
        });
    });

    describe('getNextNotificationPage generated boundary', () => {
        it('keeps the completed-page guard and emits existing state without another request', () => {
            service['courseNotificationPageMap'][123] = true;
            let emissions = 0;
            service.notifications$.subscribe(() => emissions++);
            expect(service.getNextNotificationPage(123)).toBe(false);
            httpMock.expectNone('/api/notification/courses/123?page=0&size=10');
            expect(emissions).toBe(2);
        });

        it('requests page zero and size ten, converts instants/enums and leaves wire objects untouched', () => {
            const wire = wireNotification(1);
            expect(service.getNextNotificationPage(123)).toBe(true);
            expect(service['courseNotificationMap'][123]).toEqual([]);
            const request = httpMock.expectOne('/api/notification/courses/123?page=0&size=10');
            expect(request.request.method).toBe('GET');
            request.flush(wirePage([wire]));
            const notification = service['courseNotificationMap'][123][0];
            expect(notification).toBeInstanceOf(CourseNotification);
            expect(notification.creationDate?.toISOString()).toBe('2026-10-25T00:30:00.000Z');
            expect(notification.category).toBe(CourseNotificationCategory.COMMUNICATION);
            expect(notification.status).toBe(CourseNotificationViewingStatus.UNSEEN);
            expect(notification.courseTitle).toBe('Java Programming');
            expect(notification.courseIconUrl).toBe('icon.svg');
            expect(notification.payload).toEqual(wire.payload);
            expect(wire.creationDate).toBe('2026-10-25T02:30:00+02:00');
            expect(wire.category).toBe('COMMUNICATION');
            expect(service['courseNotificationPageMap'][123]).toBe(true);
        });

        it('appends later pages in order, deduplicates IDs and retains existing notification identity/count seeding', () => {
            const existing = Array.from({ length: 10 }, (_, index) => CourseNotification.fromServer(wireNotification(index + 1)));
            service['courseNotificationMap'][123] = existing;
            service.updateNotificationCountMap(123, 17);
            const first = existing[0];
            service.getNextNotificationPage(123);
            httpMock.expectOne('/api/notification/courses/123?page=1&size=10').flush(wirePage([wireNotification(1), wireNotification(11)], 3));
            expect(service['courseNotificationMap'][123].map((notification) => notification.notificationId)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]);
            expect(service['courseNotificationMap'][123][0]).toBe(first);
            expect(service['courseNotificationPageMap'][123]).toBe(false);
            expect(service['courseNotificationCountMap'][123]).toBe(17);
        });

        it.each(['empty', 'omitted', 'null'])('keeps the %s page completion behavior', (kind) => {
            service.getNextNotificationPage(123);
            const body = kind === 'null' ? null : wirePage(kind === 'empty' ? [] : undefined, 0);
            httpMock.expectOne('/api/notification/courses/123?page=0&size=10').flush(body);
            expect(service['courseNotificationMap'][123]).toEqual([]);
            expect(service['courseNotificationPageMap'][123]).toBe(true);
        });

        it('rejects a null item before committing any converted notification or altering count state', () => {
            const page = wirePage([wireNotification(1), null, wireNotification(2)]);
            expect(() => service['convertPageFromServer'](page)).toThrow('null entry');
            expect(service['courseNotificationMap']).toEqual({});
            expect(service['courseNotificationCountMap']).toEqual({});
            expect(service['courseNotificationPageMap']).toEqual({});
            expect(page.totalElements).toBe(3);
            expect(page.content?.[1]).toBeNull();
        });
    });

    describe('setNotificationStatus generated boundary', () => {
        const statuses: { ui: CourseNotificationViewingStatus; wire: UserCourseNotificationStatusUpdateRequest['statusType'] }[] = [
            { ui: CourseNotificationViewingStatus.UNSEEN, wire: 'UNSEEN' },
            { ui: CourseNotificationViewingStatus.SEEN, wire: 'SEEN' },
            { ui: CourseNotificationViewingStatus.ARCHIVED, wire: 'ARCHIVED' },
        ];
        it.each(statuses)('sends $wire instead of the numeric UI value', ({ ui, wire }) => {
            service.setNotificationStatus(123, [0, 1, 2], ui);
            const request = httpMock.expectOne('/api/notification/courses/123/status');
            expect(request.request.method).toBe('PUT');
            expect(request.request.body).toEqual({ notificationIds: [0, 1, 2], statusType: wire });
            request.flush(null);
        });
    });

    describe('markDisplayedNotificationsAsSeen', () => {
        it('should make PUT request to the seen endpoint, not the status endpoint', () => {
            const courseId = 123;
            const notificationIds = [4, 5];

            service.markDisplayedNotificationsAsSeen(courseId, notificationIds);

            const req = httpMock.expectOne(`/api/notification/courses/${courseId}/seen`);
            expect(req.request.method).toBe('PUT');
            expect(req.request.body).toEqual({ notificationIds });
            req.flush(null);
            vi.advanceTimersByTime(0);
            httpMock.expectNone(`/api/notification/courses/${courseId}/status`);
        });
    });

    describe('archiveAll', () => {
        it('should make PUT request to archive all notifications', () => {
            const courseId = 123;

            service.archiveAll(courseId);

            const req = httpMock.expectOne(`/api/notification/courses/${courseId}/archive-all`);
            expect(req.request.method).toBe('PUT');
            expect(req.request.body).toBeNull();
            req.flush(null);
            vi.advanceTimersByTime(0);
        });
    });

    describe('archiveAllInMap', () => {
        it('should clear notifications in map and update count', () => {
            const courseId = 123;
            service['courseNotificationMap'][courseId] = [{ notificationId: 1 } as CourseNotification, { notificationId: 2 } as CourseNotification];
            const updateNotificationCountMapSpy = vi.spyOn(service, 'updateNotificationCountMap');
            const notifyNotificationSubscribersSpy = vi.spyOn(service as any, 'notifyNotificationSubscribers');

            service.archiveAllInMap(courseId);

            expect(service['courseNotificationMap'][courseId]).toEqual([]);
            expect(updateNotificationCountMapSpy).toHaveBeenCalledOnce();
            expect(updateNotificationCountMapSpy).toHaveBeenCalledWith(courseId, 0);
            expect(notifyNotificationSubscribersSpy).toHaveBeenCalledOnce();
        });
    });

    describe('setNotificationStatusInMap', () => {
        it('should update notification status in the map', () => {
            const courseId = 123;
            service['courseNotificationMap'][courseId] = [
                { notificationId: 1, status: CourseNotificationViewingStatus.UNSEEN } as CourseNotification,
                { notificationId: 2, status: CourseNotificationViewingStatus.UNSEEN } as CourseNotification,
                { notificationId: 3, status: CourseNotificationViewingStatus.UNSEEN } as CourseNotification,
            ];
            const notifyNotificationSubscribersSpy = vi.spyOn(service as any, 'notifyNotificationSubscribers');

            service.setNotificationStatusInMap(courseId, [1, 3], CourseNotificationViewingStatus.SEEN);

            expect(service['courseNotificationMap'][courseId][0].status).toBe(CourseNotificationViewingStatus.SEEN);
            expect(service['courseNotificationMap'][courseId][1].status).toBe(CourseNotificationViewingStatus.UNSEEN);
            expect(service['courseNotificationMap'][courseId][2].status).toBe(CourseNotificationViewingStatus.SEEN);
            expect(notifyNotificationSubscribersSpy).toHaveBeenCalledOnce();
        });
    });

    describe('addNotification', () => {
        it('should initialize the notifications array if it does not exist', () => {
            const courseId = 123;
            const notification = { notificationId: 1 } as CourseNotification;
            expect(service['courseNotificationMap'][courseId]).toBeUndefined();
            const addNotificationIfNotDuplicateSpy = vi.spyOn(service as any, 'addNotificationIfNotDuplicate');
            const notifyNotificationSubscribersSpy = vi.spyOn(service as any, 'notifyNotificationSubscribers');
            const incrementNotificationCountSpy = vi.spyOn(service, 'incrementNotificationCount');

            service.addNotification(courseId, notification);

            expect(service['courseNotificationMap'][courseId]).toBeDefined();
            expect(addNotificationIfNotDuplicateSpy).toHaveBeenCalledOnce();
            expect(addNotificationIfNotDuplicateSpy).toHaveBeenCalledWith(courseId, notification, true);
            expect(notifyNotificationSubscribersSpy).toHaveBeenCalledOnce();
            expect(incrementNotificationCountSpy).toHaveBeenCalledOnce();
            expect(incrementNotificationCountSpy).toHaveBeenCalledWith(courseId);
        });
    });

    describe('removeNotificationFromMap', () => {
        it('should do nothing if notifications array does not exist', () => {
            const courseId = 123;
            const notification = { notificationId: 1 } as CourseNotification;

            service.removeNotificationFromMap(courseId, notification);

            expect(service['courseNotificationMap'][courseId]).toBeUndefined();
        });

        it('should remove notification if it exists in map', () => {
            const courseId = 123;
            const notification = { notificationId: 1 } as CourseNotification;
            service['courseNotificationMap'][courseId] = [{ notificationId: 1 } as CourseNotification, { notificationId: 2 } as CourseNotification];
            const notifyNotificationSubscribersSpy = vi.spyOn(service as any, 'notifyNotificationSubscribers');
            const decreaseNotificationCountBySpy = vi.spyOn(service, 'decreaseNotificationCountBy');

            service.removeNotificationFromMap(courseId, notification);

            expect(service['courseNotificationMap'][courseId]).toHaveLength(1);
            expect(service['courseNotificationMap'][courseId][0].notificationId).toBe(2);
            expect(notifyNotificationSubscribersSpy).toHaveBeenCalledOnce();
            expect(decreaseNotificationCountBySpy).toHaveBeenCalledOnce();
            expect(decreaseNotificationCountBySpy).toHaveBeenCalledWith(courseId, 1);
        });

        it('should not modify map if notification does not exist', () => {
            const courseId = 123;
            const notification = { notificationId: 3 } as CourseNotification;
            service['courseNotificationMap'][courseId] = [{ notificationId: 1 } as CourseNotification, { notificationId: 2 } as CourseNotification];
            const notifyNotificationSubscribersSpy = vi.spyOn(service as any, 'notifyNotificationSubscribers');
            const decreaseNotificationCountBySpy = vi.spyOn(service, 'decreaseNotificationCountBy');

            service.removeNotificationFromMap(courseId, notification);

            expect(service['courseNotificationMap'][courseId]).toHaveLength(2);
            expect(notifyNotificationSubscribersSpy).not.toHaveBeenCalled();
            expect(decreaseNotificationCountBySpy).not.toHaveBeenCalled();
        });
    });

    describe('updateNotificationCountMap', () => {
        it('should update count and notify subscribers if count changes', () => {
            const courseId = 123;
            const count = 5;
            service['courseNotificationCountMap'][courseId] = 3;
            const notifyCountSubscribersSpy = vi.spyOn(service as any, 'notifyCountSubscribers');

            service.updateNotificationCountMap(courseId, count);

            expect(service['courseNotificationCountMap'][courseId]).toBe(count);
            expect(notifyCountSubscribersSpy).toHaveBeenCalledOnce();
        });

        it('should not notify subscribers if count does not change', () => {
            const courseId = 123;
            const count = 5;
            service['courseNotificationCountMap'][courseId] = count;
            const notifyCountSubscribersSpy = vi.spyOn(service as any, 'notifyCountSubscribers');

            service.updateNotificationCountMap(courseId, count);

            expect(service['courseNotificationCountMap'][courseId]).toBe(count);
            expect(notifyCountSubscribersSpy).not.toHaveBeenCalled();
        });
    });

    describe('incrementNotificationCount', () => {
        it('should initialize count to 0 if undefined and increment', () => {
            const courseId = 123;
            const notifyCountSubscribersSpy = vi.spyOn(service as any, 'notifyCountSubscribers');

            service.incrementNotificationCount(courseId);

            expect(service['courseNotificationCountMap'][courseId]).toBe(1);
            expect(notifyCountSubscribersSpy).toHaveBeenCalledOnce();
        });

        it('should increment existing count', () => {
            const courseId = 123;
            service['courseNotificationCountMap'][courseId] = 3;
            const notifyCountSubscribersSpy = vi.spyOn(service as any, 'notifyCountSubscribers');

            service.incrementNotificationCount(courseId);

            expect(service['courseNotificationCountMap'][courseId]).toBe(4);
            expect(notifyCountSubscribersSpy).toHaveBeenCalledOnce();
        });
    });

    describe('decreaseNotificationCountBy', () => {
        it('should do nothing if count is undefined', () => {
            const courseId = 123;
            const notifyCountSubscribersSpy = vi.spyOn(service as any, 'notifyCountSubscribers');

            service.decreaseNotificationCountBy(courseId, 1);

            expect(service['courseNotificationCountMap'][courseId]).toBeUndefined();
            expect(notifyCountSubscribersSpy).not.toHaveBeenCalled();
        });

        it('should decrease count and notify subscribers', () => {
            const courseId = 123;
            service['courseNotificationCountMap'][courseId] = 5;
            const notifyCountSubscribersSpy = vi.spyOn(service as any, 'notifyCountSubscribers');

            service.decreaseNotificationCountBy(courseId, 2);

            expect(service['courseNotificationCountMap'][courseId]).toBe(3);
            expect(notifyCountSubscribersSpy).toHaveBeenCalledOnce();
        });
    });

    describe('getNotificationCountForCourse$', () => {
        it('should return observable with notification count for course', async () => {
            const courseId = 123;
            service['courseNotificationCountMap'][courseId] = 5;

            const count = await firstValueFrom(service.getNotificationCountForCourse$(courseId));

            expect(count).toBe(5);
        });

        it('should return 0 if no count exists for course', async () => {
            const courseId = 123;

            const count = await firstValueFrom(service.getNotificationCountForCourse$(courseId));

            expect(count).toBe(0);
        });
    });

    describe('getNotificationsForCourse$', () => {
        it('should return observable with notifications for course', async () => {
            const courseId = 123;
            const notifications = [{ notificationId: 1 } as CourseNotification, { notificationId: 2 } as CourseNotification];
            service['courseNotificationMap'][courseId] = notifications;

            service.getNotificationsForCourse$(courseId).subscribe((result) => {
                expect(result).toBe(notifications);
            });
        });
    });

    describe('getIconFromType', () => {
        it('should return icon for known notification type', () => {
            const icon = service.getIconFromType('newPostNotification');

            expect(icon).toBe(faComments);
        });

        it('should return default icon for undefined type', () => {
            const icon = service.getIconFromType(undefined);

            expect(icon).toBe(faComments);
        });

        it('should return default icon for unknown type', () => {
            const icon = service.getIconFromType('unknownType');

            expect(icon).toBe(faComments);
        });

        it('should register an icon for the Iris review notification type', () => {
            expect(CourseNotificationService.NOTIFICATION_TYPE_ICON_MAP).toHaveProperty('irisResponseNeedsReviewNotification');
            expect(service.getIconFromType('irisResponseNeedsReviewNotification')).toBe(faComments);
        });

        it('should register an icon for the Atlas competency update notification type', () => {
            expect(service.getIconFromType('atlasCompetencyUpdateNotification')).toBe(faWandMagicSparkles);
        });
    });

    describe('DISABLE_NOTIFICATION_CHANNEL_TYPES', () => {
        it('should disable the unsupported Email and Push channels for the Iris review notification', () => {
            // The server only supports the WEBAPP channel for this notification, so Email and Push must be non-configurable.
            const disabledChannels = CourseNotificationService.DISABLE_NOTIFICATION_CHANNEL_TYPES['irisResponseNeedsReviewNotification'];

            expect(disabledChannels).toBeDefined();
            expect(disabledChannels).toContain(CourseNotificationChannel.EMAIL);
            expect(disabledChannels).toContain(CourseNotificationChannel.PUSH);
            expect(disabledChannels).not.toContain(CourseNotificationChannel.WEBAPP);
        });

        it('should only leave the Email channel configurable for the Atlas competency update notification', () => {
            // The server only supports the EMAIL channel for this notification, so Webapp and Push must be non-configurable.
            const disabledChannels = CourseNotificationService.DISABLE_NOTIFICATION_CHANNEL_TYPES['atlasCompetencyUpdateNotification'];

            expect(disabledChannels).toEqual([CourseNotificationChannel.WEBAPP, CourseNotificationChannel.PUSH]);
        });
    });

    describe('INSTRUCTOR_ONLY_NOTIFICATION_TYPES', () => {
        it('should mark the Atlas competency update notification as instructor-only', () => {
            expect(CourseNotificationService.INSTRUCTOR_ONLY_NOTIFICATION_TYPES).toContain('atlasCompetencyUpdateNotification');
        });
    });

    describe('getDateTranslationKey', () => {
        beforeEach(() => {
            vi.useFakeTimers().setSystemTime(new Date('2024-01-01T12:00:00'));
        });

        it('should return "now" for notifications created within last 5 minutes', () => {
            const notification = {
                creationDate: dayjs('2024-01-01T11:58:00'), // 2 minutes ago
            } as CourseNotification;

            const key = service.getDateTranslationKey(notification);

            expect(key).toBe('artemisApp.courseNotification.temporal.now');
        });

        it('should return "oneHourAgo" for notifications created between 5 minutes and 2 hours ago', () => {
            const notification = {
                creationDate: dayjs('2024-01-01T11:00:00'), // 1 hour ago
            } as CourseNotification;

            const key = service.getDateTranslationKey(notification);

            expect(key).toBe('artemisApp.courseNotification.temporal.oneHourAgo');
        });

        it('should return "hoursAgo" for notifications created between 2 and 9 hours ago', () => {
            const notification = {
                creationDate: dayjs('2024-01-01T05:00:00'), // 7 hours ago
            } as CourseNotification;

            const key = service.getDateTranslationKey(notification);

            expect(key).toBe('artemisApp.courseNotification.temporal.hoursAgo');
        });

        it('should return "today" for notifications created today but more than 9 hours ago', () => {
            const notification = {
                creationDate: dayjs('2024-01-01T01:00:00'),
            } as CourseNotification;

            const key = service.getDateTranslationKey(notification);

            expect(key).toBe('artemisApp.courseNotification.temporal.today');
        });

        it('should return "yesterday" for notifications created yesterday', () => {
            const notification = {
                creationDate: dayjs('2023-12-31T12:00:00'), // Yesterday
            } as CourseNotification;

            const key = service.getDateTranslationKey(notification);

            expect(key).toBe('artemisApp.courseNotification.temporal.yesterday');
        });

        it('should return "date" for notifications created before this week', () => {
            vi.spyOn(dayjs.prototype, 'startOf').mockImplementation(function () {
                if (arguments[0] === 'week') return dayjs('2023-12-31');
                return this;
            });
            vi.spyOn(dayjs.prototype, 'endOf').mockImplementation(function () {
                if (arguments[0] === 'week') return dayjs('2024-01-06');
                return this;
            });

            const notification = {
                creationDate: dayjs('2023-12-20T12:00:00'), // Before this week
            } as CourseNotification;

            const key = service.getDateTranslationKey(notification);

            expect(key).toBe('artemisApp.courseNotification.temporal.date');
        });
    });

    describe('getDateTranslationParams', () => {
        it('should return formatted translation parameters', () => {
            vi.useFakeTimers().setSystemTime(new Date('2024-01-01T12:00:00'));

            const notification = {
                creationDate: dayjs('2024-01-01T10:30:00'), // 1.5 hours ago
            } as CourseNotification;

            const params = service.getDateTranslationParams(notification);

            expect(params.hours).toBe(2); // Rounded to nearest hour
            expect(params.hour).toBe('10');
            expect(params.minute).toBe('30');
            expect(params.day).toBeDefined();
            expect(params.date).toBe('01.01.2024');
        });
    });

    describe('addNotificationIfNotDuplicate', () => {
        it('should prepend notification if not a duplicate', () => {
            const courseId = 123;
            const notification = { notificationId: 3 } as CourseNotification;
            service['courseNotificationMap'][courseId] = [{ notificationId: 1 } as CourseNotification, { notificationId: 2 } as CourseNotification];

            service['addNotificationIfNotDuplicate'](courseId, notification, true);

            expect(service['courseNotificationMap'][courseId]).toHaveLength(3);
            expect(service['courseNotificationMap'][courseId][0]).toBe(notification);
        });

        it('should append notification if not a duplicate', () => {
            const courseId = 123;
            const notification = { notificationId: 3 } as CourseNotification;
            service['courseNotificationMap'][courseId] = [{ notificationId: 1 } as CourseNotification, { notificationId: 2 } as CourseNotification];

            service['addNotificationIfNotDuplicate'](courseId, notification, false);

            expect(service['courseNotificationMap'][courseId]).toHaveLength(3);
            expect(service['courseNotificationMap'][courseId][2]).toBe(notification);
        });

        it('should not add notification if it is a duplicate', () => {
            const courseId = 123;
            const notification = { notificationId: 1 } as CourseNotification;
            service['courseNotificationMap'][courseId] = [{ notificationId: 1 } as CourseNotification, { notificationId: 2 } as CourseNotification];

            service['addNotificationIfNotDuplicate'](courseId, notification, true);

            expect(service['courseNotificationMap'][courseId]).toHaveLength(2);
        });
    });

    describe('authentication state changes', () => {
        let authState: BehaviorSubject<User | undefined>;
        let scoped: CourseNotificationService;
        let scopedHttpMock: HttpTestingController;

        beforeEach(() => {
            authState = new BehaviorSubject<User | undefined>({ id: 99 } as User);
            const customAccountService = new MockAccountService();
            customAccountService.userIdentity.set({ id: 99 } as User);
            customAccountService.getAuthenticationState = () => authState.asObservable().pipe(distinctUntilChanged());

            TestBed.resetTestingModule();
            TestBed.configureTestingModule({
                providers: [CourseNotificationService, provideHttpClient(), provideHttpClientTesting(), { provide: AccountService, useValue: customAccountService }],
            });
            scoped = TestBed.inject(CourseNotificationService);
            scopedHttpMock = TestBed.inject(HttpTestingController);
        });

        afterEach(() => {
            scopedHttpMock.verify();
        });

        it('should clear cached notifications and counts on logout', () => {
            const courseId = 7;
            scoped.addNotification(courseId, { notificationId: 1 } as CourseNotification);
            scoped.updateNotificationCountMap(courseId, 5);

            authState.next(undefined);

            expect(scoped['courseNotificationMap']).toEqual({});
            expect(scoped['courseNotificationCountMap']).toEqual({});
            expect(scoped['courseNotificationPageMap']).toEqual({});
            expect(scoped['cachedNotificationInfo']).toBeNull();
        });

        it('should clear notifications when a different user logs in', () => {
            scoped.addNotification(7, { notificationId: 1 } as CourseNotification);

            authState.next({ id: 42 } as User);

            expect(scoped['courseNotificationMap']).toEqual({});
        });

        it('should not clear notifications when the same user re-emits', () => {
            scoped.addNotification(7, { notificationId: 1 } as CourseNotification);

            authState.next({ id: 99 } as User);

            expect(scoped['courseNotificationMap'][7]).toHaveLength(1);
        });

        it('prevents an older getInfo response from caching over a new account response', () => {
            scoped.getInfo().subscribe();
            const oldRequest = scopedHttpMock.expectOne('/api/notification/courses/info');
            authState.next(new User(100));
            let current: CourseNotificationInfo | undefined;
            scoped.getInfo().subscribe((info) => (current = info));
            const fresh: CourseNotificationInfo = { channels: ['WEBAPP'], notificationTypes: { '2': 'newAnswerNotification' } };
            scopedHttpMock.expectOne('/api/notification/courses/info').flush(fresh);
            oldRequest.flush({ channels: ['EMAIL'], notificationTypes: { '1': 'newPostNotification' } });
            scoped.getInfo().subscribe((info) => (current = info));
            scopedHttpMock.expectNone('/api/notification/courses/info');
            expect(current).toEqual(fresh);
        });

        it('should ignore in-flight pagination responses after logout', () => {
            const courseId = 7;
            scoped.getNextNotificationPage(courseId);
            const inFlight = scopedHttpMock.expectOne(`/api/notification/courses/${courseId}?page=0&size=10`);

            authState.next(undefined);

            inFlight.flush({ content: [{ notificationId: 99 } as CourseNotification], totalPages: 1 });
            vi.advanceTimersByTime(0);

            expect(scoped['courseNotificationMap']).toEqual({});
        });
    });
});
