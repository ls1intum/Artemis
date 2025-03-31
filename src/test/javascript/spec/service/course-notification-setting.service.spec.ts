import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CourseNotificationSettingService } from 'app/communication/course-notification/course-notification-setting.service';
import { CourseNotificationSettingInfo } from 'app/communication/shared/entities/course-notification/course-notification-setting-info';
import { CourseNotificationSettingSpecification } from 'app/communication/shared/entities/course-notification/course-notification-setting-specification';
import { CourseNotificationChannel } from 'app/communication/shared/entities/course-notification/course-notification-channel';
import { CourseNotificationChannelSetting } from 'app/communication/shared/entities/course-notification/course-notification-channel-setting';

describe('CourseNotificationSettingService', () => {
    let service: CourseNotificationSettingService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [CourseNotificationSettingService, provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(CourseNotificationSettingService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        jest.restoreAllMocks();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('getSettingInfo', () => {
        it('should make GET request to fetch notification settings', fakeAsync(() => {
            const courseId = 123;
            const mockSettingInfo: CourseNotificationSettingInfo = {
                selectedPreset: 1,
                notificationTypeChannels: {
                    newPostNotification: {
                        [CourseNotificationChannel.PUSH]: true,
                        [CourseNotificationChannel.EMAIL]: false,
                        [CourseNotificationChannel.WEBAPP]: true,
                    },
                },
            };

            let result: CourseNotificationSettingInfo | undefined;
            service.getSettingInfo(courseId).subscribe((response) => {
                result = response.body || undefined;
            });

            const req = httpMock.expectOne(`/api/communication/notification/${courseId}/settings`);
            expect(req.request.method).toBe('GET');
            req.flush(mockSettingInfo);
            tick();

            expect(result).toEqual(mockSettingInfo);
        }));
    });

    describe('setSettingPreset', () => {
        it('should make PUT request to set notification preset', fakeAsync(() => {
            const courseId = 123;
            const presetTypeId = 2;

            service.setSettingPreset(courseId, presetTypeId);

            const req = httpMock.expectOne(`/api/communication/notification/${courseId}/setting-preset`);
            expect(req.request.method).toBe('PUT');
            expect(req.request.body).toBe(presetTypeId);
            req.flush({});
            tick();
        }));
    });

    describe('setSettingSpecification', () => {
        it('should make PUT request with transformed specifications', fakeAsync(() => {
            const courseId = 123;
            const channelSetting1: CourseNotificationChannelSetting = {
                [CourseNotificationChannel.PUSH]: true,
                [CourseNotificationChannel.EMAIL]: false,
                [CourseNotificationChannel.WEBAPP]: true,
            };
            const channelSetting2: CourseNotificationChannelSetting = {
                [CourseNotificationChannel.PUSH]: false,
                [CourseNotificationChannel.EMAIL]: true,
                [CourseNotificationChannel.WEBAPP]: true,
            };

            const specifications: CourseNotificationSettingSpecification[] = [
                new CourseNotificationSettingSpecification('notification1', 1, channelSetting1),
                new CourseNotificationSettingSpecification('notification2', 2, channelSetting2),
            ];

            service.setSettingSpecification(courseId, specifications);

            const req = httpMock.expectOne(`/api/communication/notification/${courseId}/setting-specification`);
            expect(req.request.method).toBe('PUT');
            expect(req.request.body).toEqual({
                notificationTypeChannels: {
                    1: channelSetting1,
                    2: channelSetting2,
                },
            });
            req.flush({});
            tick();
        }));
    });

    describe('transformNotificationSettingSpecificationToRequestBody', () => {
        it('should transform an array of specifications to a record by typeId', () => {
            const channelSetting1: CourseNotificationChannelSetting = {
                [CourseNotificationChannel.PUSH]: true,
                [CourseNotificationChannel.EMAIL]: false,
                [CourseNotificationChannel.WEBAPP]: true,
            };
            const channelSetting2: CourseNotificationChannelSetting = {
                [CourseNotificationChannel.PUSH]: false,
                [CourseNotificationChannel.EMAIL]: true,
                [CourseNotificationChannel.WEBAPP]: true,
            };

            const specifications: CourseNotificationSettingSpecification[] = [
                new CourseNotificationSettingSpecification('notification1', 1, channelSetting1),
                new CourseNotificationSettingSpecification('notification2', 2, channelSetting2),
            ];

            const result = service['transformNotificationSettingSpecificationToRequestBody'](specifications);

            expect(result).toEqual({
                1: channelSetting1,
                2: channelSetting2,
            });
        });

        it('should handle empty specifications array', () => {
            const result = service['transformNotificationSettingSpecificationToRequestBody']([]);

            expect(result).toEqual({});
        });
    });
});
