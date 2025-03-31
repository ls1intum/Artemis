import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { CourseNotificationService } from 'app/communication/course-notification/course-notification.service';
import { HttpResponse } from '@angular/common/http';
import { CourseNotification } from 'app/communication/shared/entities/course-notification/course-notification';
import { CourseNotificationViewingStatus } from 'app/communication/shared/entities/course-notification/course-notification-viewing-status';
import { CourseNotificationPage } from 'app/communication/shared/entities/course-notification/course-notification-page';
import { CourseNotificationCategory } from 'app/communication/shared/entities/course-notification/course-notification-category';
import dayjs from 'dayjs/esm';
import { faComments } from '@fortawesome/free-solid-svg-icons';
import { firstValueFrom } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('CourseNotificationService', () => {
    let service: CourseNotificationService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [CourseNotificationService, provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(CourseNotificationService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        jest.restoreAllMocks();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('getNextNotificationPage', () => {
        it('should return false if final page was already reached', () => {
            const courseId = 123;
            service['courseNotificationPageMap'][courseId] = true;
            const notifyNotificationSubscribersSpy = jest.spyOn(service as any, 'notifyNotificationSubscribers');

            const result = service.getNextNotificationPage(courseId);

            expect(result).toBeFalse();
            expect(notifyNotificationSubscribersSpy).toHaveBeenCalledOnce();
        });

        it('should initialize the notifications array if it does not exist', fakeAsync(() => {
            const courseId = 123;
            expect(service['courseNotificationMap'][courseId]).toBeUndefined();

            service.getNextNotificationPage(courseId);

            expect(service['courseNotificationMap'][courseId]).toEqual([]);

            const req = httpMock.expectOne(`/api/communication/notification/${courseId}?page=0&size=10`);
            req.flush({ content: [], totalPages: 0 });
            tick();
        }));

        it('should fetch notification page and handle response with content', fakeAsync(() => {
            const courseId = 123;

            const mockNotifications: CourseNotification[] = [
                {
                    notificationId: 1,
                    type: 'newPostNotification',
                    status: 'UNSEEN' as unknown as CourseNotificationViewingStatus,
                    // @ts-ignore
                    creationDate: new Date('2024-01-01'),
                    category: 'GENERAL' as unknown as CourseNotificationCategory,
                },
                {
                    notificationId: 2,
                    type: 'newPostNotification',
                    status: 'UNSEEN' as unknown as CourseNotificationViewingStatus,
                    // @ts-ignore
                    creationDate: new Date('2024-01-02'),
                    category: 'GENERAL' as unknown as CourseNotificationCategory,
                },
            ];
            const mockResponse: { content: CourseNotification[]; totalPages: number } = {
                content: mockNotifications,
                totalPages: 1,
            };
            const notifyNotificationSubscribersSpy = jest.spyOn(service as any, 'notifyNotificationSubscribers');

            const result = service.getNextNotificationPage(courseId);

            const req = httpMock.expectOne(`/api/communication/notification/${courseId}?page=0&size=10`);
            expect(req.request.method).toBe('GET');
            req.flush(mockResponse);
            tick();

            expect(result).toBeTrue();
            expect(service['courseNotificationMap'][courseId]).toHaveLength(2);
            expect(service['courseNotificationPageMap'][courseId]).toBeTrue();
            expect(notifyNotificationSubscribersSpy).toHaveBeenCalledOnce();
        }));

        it('should handle empty response', fakeAsync(() => {
            const courseId = 123;
            const notifyNotificationSubscribersSpy = jest.spyOn(service as any, 'notifyNotificationSubscribers');

            service.getNextNotificationPage(courseId);

            const req = httpMock.expectOne(`/api/communication/notification/${courseId}?page=0&size=10`);
            req.flush(null);
            tick();

            expect(service['courseNotificationPageMap'][courseId]).toBeTrue();
            expect(notifyNotificationSubscribersSpy).toHaveBeenCalledOnce();
        }));
    });

    describe('setNotificationStatus', () => {
        it('should make PUT request to set notification status', fakeAsync(() => {
            // Arrange
            const courseId = 123;
            const notificationIds = [1, 2, 3];
            const statusType = CourseNotificationViewingStatus.SEEN;

            service.setNotificationStatus(courseId, notificationIds, statusType);

            const req = httpMock.expectOne(`/api/communication/notification/${courseId}/status`);
            expect(req.request.method).toBe('PUT');
            expect(req.request.body).toEqual({
                notificationIds,
                statusType,
            });
            req.flush({});
            tick();
        }));
    });

    describe('archiveAll', () => {
        it('should make PUT request to archive all notifications', fakeAsync(() => {
            const courseId = 123;

            service.archiveAll(courseId);

            const req = httpMock.expectOne(`/api/communication/notification/${courseId}/archive-all`);
            expect(req.request.method).toBe('PUT');
            expect(req.request.body).toEqual({});
            req.flush({});
            tick();
        }));
    });

    describe('archiveAllInMap', () => {
        it('should clear notifications in map and update count', () => {
            const courseId = 123;
            service['courseNotificationMap'][courseId] = [{ notificationId: 1 } as CourseNotification, { notificationId: 2 } as CourseNotification];
            const updateNotificationCountMapSpy = jest.spyOn(service, 'updateNotificationCountMap');
            const notifyNotificationSubscribersSpy = jest.spyOn(service as any, 'notifyNotificationSubscribers');

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
            const notifyNotificationSubscribersSpy = jest.spyOn(service as any, 'notifyNotificationSubscribers');

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
            const addNotificationIfNotDuplicateSpy = jest.spyOn(service as any, 'addNotificationIfNotDuplicate');
            const notifyNotificationSubscribersSpy = jest.spyOn(service as any, 'notifyNotificationSubscribers');
            const incrementNotificationCountSpy = jest.spyOn(service, 'incrementNotificationCount');

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
            const notifyNotificationSubscribersSpy = jest.spyOn(service as any, 'notifyNotificationSubscribers');
            const decreaseNotificationCountBySpy = jest.spyOn(service, 'decreaseNotificationCountBy');

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
            const notifyNotificationSubscribersSpy = jest.spyOn(service as any, 'notifyNotificationSubscribers');
            const decreaseNotificationCountBySpy = jest.spyOn(service, 'decreaseNotificationCountBy');

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
            const notifyCountSubscribersSpy = jest.spyOn(service as any, 'notifyCountSubscribers');

            service.updateNotificationCountMap(courseId, count);

            expect(service['courseNotificationCountMap'][courseId]).toBe(count);
            expect(notifyCountSubscribersSpy).toHaveBeenCalledOnce();
        });

        it('should not notify subscribers if count does not change', () => {
            const courseId = 123;
            const count = 5;
            service['courseNotificationCountMap'][courseId] = count;
            const notifyCountSubscribersSpy = jest.spyOn(service as any, 'notifyCountSubscribers');

            service.updateNotificationCountMap(courseId, count);

            expect(service['courseNotificationCountMap'][courseId]).toBe(count);
            expect(notifyCountSubscribersSpy).not.toHaveBeenCalled();
        });
    });

    describe('incrementNotificationCount', () => {
        it('should initialize count to 0 if undefined and increment', () => {
            const courseId = 123;
            const notifyCountSubscribersSpy = jest.spyOn(service as any, 'notifyCountSubscribers');

            service.incrementNotificationCount(courseId);

            expect(service['courseNotificationCountMap'][courseId]).toBe(1);
            expect(notifyCountSubscribersSpy).toHaveBeenCalledOnce();
        });

        it('should increment existing count', () => {
            const courseId = 123;
            service['courseNotificationCountMap'][courseId] = 3;
            const notifyCountSubscribersSpy = jest.spyOn(service as any, 'notifyCountSubscribers');

            service.incrementNotificationCount(courseId);

            expect(service['courseNotificationCountMap'][courseId]).toBe(4);
            expect(notifyCountSubscribersSpy).toHaveBeenCalledOnce();
        });
    });

    describe('decreaseNotificationCountBy', () => {
        it('should do nothing if count is undefined', () => {
            const courseId = 123;
            const notifyCountSubscribersSpy = jest.spyOn(service as any, 'notifyCountSubscribers');

            service.decreaseNotificationCountBy(courseId, 1);

            expect(service['courseNotificationCountMap'][courseId]).toBeUndefined();
            expect(notifyCountSubscribersSpy).not.toHaveBeenCalled();
        });

        it('should decrease count and notify subscribers', () => {
            const courseId = 123;
            service['courseNotificationCountMap'][courseId] = 5;
            const notifyCountSubscribersSpy = jest.spyOn(service as any, 'notifyCountSubscribers');

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

        // TODO: Add other notification types here

        it('should return default icon for undefined type', () => {
            const icon = service.getIconFromType(undefined);

            expect(icon).toBe(faComments);
        });

        it('should return default icon for unknown type', () => {
            const icon = service.getIconFromType('unknownType');

            expect(icon).toBe(faComments);
        });
    });

    describe('getDateTranslationKey', () => {
        beforeEach(() => {
            jest.useFakeTimers().setSystemTime(new Date('2024-01-01T12:00:00'));
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
            jest.spyOn(dayjs.prototype, 'startOf').mockImplementation(function (this: dayjs.Dayjs) {
                if (arguments[0] === 'week') return dayjs('2023-12-31');
                return this;
            });
            jest.spyOn(dayjs.prototype, 'endOf').mockImplementation(function (this: dayjs.Dayjs) {
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
            jest.useFakeTimers().setSystemTime(new Date('2024-01-01T12:00:00'));

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

    describe('convertResponseFromServer', () => {
        it('should convert dates and enums in response', () => {
            const mockResponse = {
                body: {
                    content: [
                        {
                            notificationId: 1,
                            creationDate: '2024-01-01T10:30:00Z',
                            category: 'DISCUSSION',
                            status: 'UNSEEN',
                            parameters: {
                                courseTitle: 'Java Programming',
                                courseIconUrl: 'http://example.com/icon.png',
                            },
                        },
                    ],
                    totalPages: 1,
                },
            } as unknown as HttpResponse<CourseNotificationPage>;

            jest.mock('app/shared/util/date.utils', () => ({
                convertDateFromServer: (date: any) => {
                    if (date === '2024-01-01T10:30:00Z') {
                        return dayjs('2024-01-01T10:30:00Z');
                    }
                    return date;
                },
            }));

            const result = service['convertResponseFromServer'](mockResponse);

            expect(result.body!.content![0].courseName).toBe('Java Programming');
            expect(result.body!.content![0].courseIconUrl).toBe('http://example.com/icon.png');
        });
    });
});
