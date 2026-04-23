import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CourseNotificationSettingService } from 'app/communication/course-notification/course-notification-setting.service';
import { CourseNotificationSettingInfo } from 'app/communication/shared/entities/course-notification/course-notification-setting-info';
import { CourseNotificationSettingSpecification } from 'app/communication/shared/entities/course-notification/course-notification-setting-specification';
import { CourseNotificationChannel } from 'app/communication/shared/entities/course-notification/course-notification-channel';
import { CourseNotificationChannelSetting } from 'app/communication/shared/entities/course-notification/course-notification-channel-setting';
import { CourseNotificationSettingPreset } from 'app/communication/shared/entities/course-notification/course-notification-setting-preset';

describe('CourseNotificationSettingService', () => {
    setupTestBed({ zoneless: true });

    let service: CourseNotificationSettingService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        vi.useFakeTimers();
        TestBed.configureTestingModule({
            providers: [CourseNotificationSettingService, provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(CourseNotificationSettingService);
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

    describe('getSettingInfo', () => {
        it('should make GET request to fetch notification settings', () => {
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
                result = response;
            });

            const req = httpMock.expectOne(`/api/communication/notification/${courseId}/settings`);
            expect(req.request.method).toBe('GET');
            req.flush(mockSettingInfo);
            vi.advanceTimersByTime(0);

            expect(result).toEqual(mockSettingInfo);
        });

        it('should return cached value if available and forceFetch is false', () => {
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

            service.getSettingInfo(courseId).subscribe();
            const firstReq = httpMock.expectOne(`/api/communication/notification/${courseId}/settings`);
            firstReq.flush(mockSettingInfo);
            vi.advanceTimersByTime(0);

            let result: CourseNotificationSettingInfo | undefined;
            service.getSettingInfo(courseId).subscribe((response) => {
                result = response;
            });
            vi.advanceTimersByTime(0);

            httpMock.expectNone(`/api/communication/notification/${courseId}/settings`);
            expect(result).toEqual(mockSettingInfo);
        });

        it('should make a new request if forceFetch is true, even with cached value', () => {
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

            const updatedMockSettingInfo: CourseNotificationSettingInfo = {
                ...mockSettingInfo,
                selectedPreset: 2,
            };

            service.getSettingInfo(courseId).subscribe();
            const firstReq = httpMock.expectOne(`/api/communication/notification/${courseId}/settings`);
            firstReq.flush(mockSettingInfo);
            vi.advanceTimersByTime(0);

            let result: CourseNotificationSettingInfo | undefined;
            service.getSettingInfo(courseId, true).subscribe((response) => {
                result = response;
            });

            const secondReq = httpMock.expectOne(`/api/communication/notification/${courseId}/settings`);
            secondReq.flush(updatedMockSettingInfo);
            vi.advanceTimersByTime(0);

            expect(result).toEqual(updatedMockSettingInfo);
        });
    });

    describe('setSettingPreset', () => {
        it('should make PUT request to set notification preset', () => {
            const courseId = 123;
            const presetTypeId = 2;

            service.setSettingPreset(courseId, presetTypeId, undefined);

            const req = httpMock.expectOne(`/api/communication/notification/${courseId}/setting-preset`);
            expect(req.request.method).toBe('PUT');
            expect(req.request.body).toBe(presetTypeId);
            req.flush({});
            vi.advanceTimersByTime(0);
        });

        it('should update the subject with new preset when current value exists', () => {
            const courseId = 123;
            const presetTypeId = 2;
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

            service.getSettingInfo(courseId).subscribe();
            const firstReq = httpMock.expectOne(`/api/communication/notification/${courseId}/settings`);
            firstReq.flush(mockSettingInfo);
            vi.advanceTimersByTime(0);

            service.setSettingPreset(courseId, presetTypeId, undefined);
            const updateReq = httpMock.expectOne(`/api/communication/notification/${courseId}/setting-preset`);
            updateReq.flush({});
            vi.advanceTimersByTime(0);

            let result: CourseNotificationSettingInfo | undefined;
            service.getSettingInfo(courseId).subscribe((response) => {
                result = response;
            });
            vi.advanceTimersByTime(0);

            expect(result).toBeDefined();
            expect(result?.selectedPreset).toBe(presetTypeId);
        });

        it('should copy preset map when copyPreset is provided', () => {
            const courseId = 123;
            const presetTypeId = 2;
            const mockSettingInfo: CourseNotificationSettingInfo = {
                selectedPreset: 1,
                notificationTypeChannels: {
                    oldNotification: {
                        [CourseNotificationChannel.PUSH]: false,
                        [CourseNotificationChannel.EMAIL]: false,
                        [CourseNotificationChannel.WEBAPP]: false,
                    },
                },
            };

            const mockPreset: CourseNotificationSettingPreset = {
                identifier: 'preset1',
                typeId: 1,
                presetMap: {
                    newNotification: {
                        [CourseNotificationChannel.PUSH]: true,
                        [CourseNotificationChannel.EMAIL]: true,
                        [CourseNotificationChannel.WEBAPP]: true,
                    },
                },
            };

            service.getSettingInfo(courseId).subscribe();
            const firstReq = httpMock.expectOne(`/api/communication/notification/${courseId}/settings`);
            firstReq.flush(mockSettingInfo);
            vi.advanceTimersByTime(0);

            service.setSettingPreset(courseId, presetTypeId, mockPreset);
            const updateReq = httpMock.expectOne(`/api/communication/notification/${courseId}/setting-preset`);
            updateReq.flush({});
            vi.advanceTimersByTime(0);

            let result: CourseNotificationSettingInfo | undefined;
            service.getSettingInfo(courseId).subscribe((response) => {
                result = response;
            });
            vi.advanceTimersByTime(0);

            expect(result).toBeDefined();
            expect(result?.selectedPreset).toBe(presetTypeId);
            expect(result?.notificationTypeChannels).toEqual(mockPreset.presetMap);
        });
    });

    describe('setSettingSpecification', () => {
        it('should make PUT request with transformed specifications', () => {
            const courseId = 123;
            const channelSetting: CourseNotificationChannelSetting = {
                [CourseNotificationChannel.PUSH]: true,
                [CourseNotificationChannel.EMAIL]: false,
                [CourseNotificationChannel.WEBAPP]: true,
            };

            const specification = new CourseNotificationSettingSpecification('notification1', 1, channelSetting);

            service.setSettingSpecification(courseId, specification, undefined);

            const req = httpMock.expectOne(`/api/communication/notification/${courseId}/setting-specification`);
            expect(req.request.method).toBe('PUT');
            expect(req.request.body).toEqual({
                notificationTypeChannels: {
                    1: channelSetting,
                },
            });
            req.flush({});
            vi.advanceTimersByTime(0);
        });

        it('should update the subject with new specification when current value exists', () => {
            const courseId = 123;
            const mockSettingInfo: CourseNotificationSettingInfo = {
                selectedPreset: 1,
                notificationTypeChannels: {
                    oldNotification: {
                        [CourseNotificationChannel.PUSH]: false,
                        [CourseNotificationChannel.EMAIL]: false,
                        [CourseNotificationChannel.WEBAPP]: false,
                    },
                },
            };

            const channelSetting: CourseNotificationChannelSetting = {
                [CourseNotificationChannel.PUSH]: true,
                [CourseNotificationChannel.EMAIL]: false,
                [CourseNotificationChannel.WEBAPP]: true,
            };

            const specification = new CourseNotificationSettingSpecification('notification1', 1, channelSetting);

            service.getSettingInfo(courseId).subscribe();
            const firstReq = httpMock.expectOne(`/api/communication/notification/${courseId}/settings`);
            firstReq.flush(mockSettingInfo);
            vi.advanceTimersByTime(0);

            service.setSettingSpecification(courseId, specification, undefined);
            const updateReq = httpMock.expectOne(`/api/communication/notification/${courseId}/setting-specification`);
            updateReq.flush({});
            vi.advanceTimersByTime(0);

            let result: CourseNotificationSettingInfo | undefined;
            service.getSettingInfo(courseId).subscribe((response) => {
                result = response;
            });
            vi.advanceTimersByTime(0);

            expect(result).toBeDefined();
            expect(result?.selectedPreset).toBe(0);
            expect(result?.notificationTypeChannels['notification1']).toEqual(channelSetting);
        });

        it('should copy preset map when copyPreset is provided', () => {
            const courseId = 123;
            const mockSettingInfo: CourseNotificationSettingInfo = {
                selectedPreset: 1,
                notificationTypeChannels: {
                    oldNotification: {
                        [CourseNotificationChannel.PUSH]: false,
                        [CourseNotificationChannel.EMAIL]: false,
                        [CourseNotificationChannel.WEBAPP]: false,
                    },
                },
            };

            const mockPreset: CourseNotificationSettingPreset = {
                identifier: 'preset1',
                typeId: 1,
                presetMap: {
                    newNotification: {
                        [CourseNotificationChannel.PUSH]: true,
                        [CourseNotificationChannel.EMAIL]: true,
                        [CourseNotificationChannel.WEBAPP]: true,
                    },
                },
            };

            const channelSetting: CourseNotificationChannelSetting = {
                [CourseNotificationChannel.PUSH]: true,
                [CourseNotificationChannel.EMAIL]: false,
                [CourseNotificationChannel.WEBAPP]: true,
            };

            const specification = new CourseNotificationSettingSpecification('notification1', 1, channelSetting);

            service.getSettingInfo(courseId).subscribe();
            const firstReq = httpMock.expectOne(`/api/communication/notification/${courseId}/settings`);
            firstReq.flush(mockSettingInfo);
            vi.advanceTimersByTime(0);

            service.setSettingSpecification(courseId, specification, mockPreset);
            const updateReq = httpMock.expectOne(`/api/communication/notification/${courseId}/setting-specification`);
            updateReq.flush({});
            vi.advanceTimersByTime(0);

            let result: CourseNotificationSettingInfo | undefined;
            service.getSettingInfo(courseId).subscribe((response) => {
                result = response;
            });
            vi.advanceTimersByTime(0);

            expect(result).toBeDefined();
            expect(result?.selectedPreset).toBe(0);
            expect(result?.notificationTypeChannels).toHaveProperty('newNotification');
            expect(result?.notificationTypeChannels['notification1']).toEqual(channelSetting);
        });
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
