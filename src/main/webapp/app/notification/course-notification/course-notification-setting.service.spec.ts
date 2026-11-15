import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CourseNotificationSettingService } from 'app/notification/course-notification/course-notification-setting.service';
import { CourseNotificationSettingInfo } from 'app/notification/shared/entities/course-notification/course-notification-setting-info';
import { CourseNotificationSettingSpecification } from 'app/notification/shared/entities/course-notification/course-notification-setting-specification';
import { CourseNotificationChannel } from 'app/notification/shared/entities/course-notification/course-notification-channel';
import { CourseNotificationChannelSetting } from 'app/notification/shared/entities/course-notification/course-notification-channel-setting';
import { CourseNotificationSettingPreset } from 'app/notification/shared/entities/course-notification/course-notification-setting-preset';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { BehaviorSubject, distinctUntilChanged, firstValueFrom } from 'rxjs';
import { User } from 'app/account/user/user.model';

describe('CourseNotificationSettingService', () => {
    let service: CourseNotificationSettingService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        vi.useFakeTimers();
        TestBed.configureTestingModule({
            providers: [CourseNotificationSettingService, provideHttpClient(), provideHttpClientTesting(), { provide: AccountService, useClass: MockAccountService }],
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

            const req = httpMock.expectOne(`/api/notification/courses/${courseId}/settings`);
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
            const firstReq = httpMock.expectOne(`/api/notification/courses/${courseId}/settings`);
            firstReq.flush(mockSettingInfo);
            vi.advanceTimersByTime(0);

            let result: CourseNotificationSettingInfo | undefined;
            service.getSettingInfo(courseId).subscribe((response) => {
                result = response;
            });
            vi.advanceTimersByTime(0);

            httpMock.expectNone(`/api/notification/courses/${courseId}/settings`);
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
            const firstReq = httpMock.expectOne(`/api/notification/courses/${courseId}/settings`);
            firstReq.flush(mockSettingInfo);
            vi.advanceTimersByTime(0);

            let result: CourseNotificationSettingInfo | undefined;
            service.getSettingInfo(courseId, true).subscribe((response) => {
                result = response;
            });

            const secondReq = httpMock.expectOne(`/api/notification/courses/${courseId}/settings`);
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

            const req = httpMock.expectOne(`/api/notification/courses/${courseId}/setting-preset`);
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
            const firstReq = httpMock.expectOne(`/api/notification/courses/${courseId}/settings`);
            firstReq.flush(mockSettingInfo);
            vi.advanceTimersByTime(0);

            service.setSettingPreset(courseId, presetTypeId, undefined);
            const updateReq = httpMock.expectOne(`/api/notification/courses/${courseId}/setting-preset`);
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
            const firstReq = httpMock.expectOne(`/api/notification/courses/${courseId}/settings`);
            firstReq.flush(mockSettingInfo);
            vi.advanceTimersByTime(0);

            service.setSettingPreset(courseId, presetTypeId, mockPreset);
            const updateReq = httpMock.expectOne(`/api/notification/courses/${courseId}/setting-preset`);
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

            const req = httpMock.expectOne(`/api/notification/courses/${courseId}/setting-specification`);
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
            const firstReq = httpMock.expectOne(`/api/notification/courses/${courseId}/settings`);
            firstReq.flush(mockSettingInfo);
            vi.advanceTimersByTime(0);

            service.setSettingSpecification(courseId, specification, undefined);
            const updateReq = httpMock.expectOne(`/api/notification/courses/${courseId}/setting-specification`);
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
            const firstReq = httpMock.expectOne(`/api/notification/courses/${courseId}/settings`);
            firstReq.flush(mockSettingInfo);
            vi.advanceTimersByTime(0);

            service.setSettingSpecification(courseId, specification, mockPreset);
            const updateReq = httpMock.expectOne(`/api/notification/courses/${courseId}/setting-specification`);
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

        it('should not change the shared preset when the channels of a course are customized from it', () => {
            const courseId = 123;
            const preset: CourseNotificationSettingPreset = {
                identifier: 'default',
                typeId: 1,
                presetMap: {
                    notification1: { [CourseNotificationChannel.PUSH]: true, [CourseNotificationChannel.EMAIL]: true, [CourseNotificationChannel.WEBAPP]: true },
                },
            };
            const customized = new CourseNotificationSettingSpecification('notification1', 1, {
                [CourseNotificationChannel.PUSH]: false,
                [CourseNotificationChannel.EMAIL]: true,
                [CourseNotificationChannel.WEBAPP]: true,
            });

            service.getSettingInfo(courseId).subscribe();
            httpMock.expectOne(`/api/notification/courses/${courseId}/settings`).flush({ selectedPreset: 1, notificationTypeChannels: {} });
            vi.advanceTimersByTime(0);
            service.setSettingSpecification(courseId, customized, preset);
            httpMock.expectOne(`/api/notification/courses/${courseId}/setting-specification`).flush({});

            // Another course customized from the same preset must still start from what the preset says.
            expect(preset.presetMap['notification1'][CourseNotificationChannel.PUSH]).toBe(true);
        });

        it('should keep the channels that apply to the user when there is no preset to copy', () => {
            const courseId = 123;
            // What the server reports for an unconfigured test account: the custom preset, without e-mail.
            const effectiveSettingInfo: CourseNotificationSettingInfo = {
                selectedPreset: 0,
                notificationTypeChannels: {
                    '1': { [CourseNotificationChannel.PUSH]: true, [CourseNotificationChannel.EMAIL]: false, [CourseNotificationChannel.WEBAPP]: true },
                    '2': { [CourseNotificationChannel.PUSH]: false, [CourseNotificationChannel.EMAIL]: false, [CourseNotificationChannel.WEBAPP]: true },
                },
            };
            const changed = new CourseNotificationSettingSpecification('notification1', 1, {
                [CourseNotificationChannel.PUSH]: false,
                [CourseNotificationChannel.EMAIL]: false,
                [CourseNotificationChannel.WEBAPP]: true,
            });

            service.getSettingInfo(courseId).subscribe();
            httpMock.expectOne(`/api/notification/courses/${courseId}/settings`).flush(effectiveSettingInfo);
            vi.advanceTimersByTime(0);
            service.setSettingSpecification(courseId, changed, undefined);
            const request = httpMock.expectOne(`/api/notification/courses/${courseId}/setting-specification`);
            request.flush({});
            vi.advanceTimersByTime(0);

            let result: CourseNotificationSettingInfo | undefined;
            service.getSettingInfo(courseId).subscribe((response) => (result = response));
            vi.advanceTimersByTime(0);

            // Only the changed type is submitted, with the e-mail the user was shown, and the others keep what applies to them.
            expect(request.request.body).toEqual({ notificationTypeChannels: { 1: changed.channelSetting } });
            expect(result?.selectedPreset).toBe(0);
            expect(result?.notificationTypeChannels['2'][CourseNotificationChannel.EMAIL]).toBe(false);
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

    describe('authentication state changes', () => {
        let authState: BehaviorSubject<User | undefined>;
        let scoped: CourseNotificationSettingService;
        let scopedHttpMock: HttpTestingController;

        beforeEach(() => {
            authState = new BehaviorSubject<User | undefined>({ id: 99 } as User);
            const customAccountService = new MockAccountService();
            customAccountService.userIdentity.set({ id: 99 } as User);
            customAccountService.getAuthenticationState = () => authState.asObservable().pipe(distinctUntilChanged());

            TestBed.resetTestingModule();
            TestBed.configureTestingModule({
                providers: [CourseNotificationSettingService, provideHttpClient(), provideHttpClientTesting(), { provide: AccountService, useValue: customAccountService }],
            });
            scoped = TestBed.inject(CourseNotificationSettingService);
            scopedHttpMock = TestBed.inject(HttpTestingController);
        });

        afterEach(() => {
            scopedHttpMock.verify();
        });

        it('should clear cached setting subjects on logout', async () => {
            const courseId = 7;
            const settingInfo: CourseNotificationSettingInfo = { selectedPreset: 1, notificationTypeChannels: {} };
            scoped.getSettingInfo(courseId).subscribe();
            scopedHttpMock.expectOne(`/api/notification/courses/${courseId}/settings`).flush(settingInfo);
            const before = await firstValueFrom(scoped.getSettingInfo(courseId));
            expect(before).toEqual(settingInfo);

            authState.next(undefined);

            // Subsequent getSettingInfo creates a fresh subject and triggers a new HTTP request
            const after$ = scoped.getSettingInfo(courseId);
            scopedHttpMock.expectOne(`/api/notification/courses/${courseId}/settings`).flush(settingInfo);
            const after = await firstValueFrom(after$);
            expect(after).toEqual(settingInfo);
        });

        it('should ignore in-flight HTTP responses after logout', async () => {
            const courseId = 7;
            scoped.getSettingInfo(courseId).subscribe();
            const inFlight = scopedHttpMock.expectOne(`/api/notification/courses/${courseId}/settings`);

            authState.next(undefined);

            inFlight.flush({ selectedPreset: 99, notificationTypeChannels: {} } as CourseNotificationSettingInfo);

            // Internal subject map should have been cleared, so getSettingInfo creates a fresh subject.
            expect(Object.keys(scoped['settingInfoSubjects'])).toHaveLength(0);
        });

        it('should not clear settings when the same user re-emits', () => {
            const courseId = 7;
            scoped.getSettingInfo(courseId).subscribe();
            scopedHttpMock.expectOne(`/api/notification/courses/${courseId}/settings`).flush({ selectedPreset: 1, notificationTypeChannels: {} });

            authState.next({ id: 99 } as User);

            expect(scoped['settingInfoSubjects'][courseId]).toBeDefined();
        });
    });
});
