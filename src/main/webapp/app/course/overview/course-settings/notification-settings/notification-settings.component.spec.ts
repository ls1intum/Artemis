import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NotificationSettingsComponent } from 'app/course/overview/course-settings/notification-settings/notification-settings.component';
import { CourseNotificationSettingInfo, CourseNotificationSettingService } from 'app/notification/course-notification/course-notification-setting.service';
import { CourseNotificationService } from 'app/notification/course-notification/course-notification.service';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { UserCourseNotificationSettingPreset as CourseNotificationSettingPreset } from 'app/openapi/model/user-course-notification-setting-preset';
import { CourseNotificationInfo } from 'app/openapi/model/course-notification-info';
import { CourseNotificationSettingSpecification } from 'app/notification/shared/entities/course-notification/course-notification-setting-specification';
import { CourseNotificationChannel } from 'app/notification/shared/entities/course-notification/course-notification-channel';
import { By } from '@angular/platform-browser';
import { MockComponent } from 'ng-mocks';
import { CourseNotificationPresetPickerComponent } from 'app/notification/course-notification/course-notification-preset-picker/course-notification-preset-picker.component';
import { CourseNotificationSettingSpecificationCardComponent } from 'app/notification/course-notification/course-notification-setting-specification-card/course-notification-setting-specification-card.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockDirective } from 'ng-mocks';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CourseNotificationSettingsMap } from 'app/notification/shared/entities/course-notification/course-notification-settings-map';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('NotificationSettingsComponent', () => {
    let component: NotificationSettingsComponent;
    let fixture: ComponentFixture<NotificationSettingsComponent>;
    let courseNotificationServiceMock: {
        getInfo: ReturnType<typeof vi.fn>;
        getIconFromType: ReturnType<typeof vi.fn>;
        getDateTranslationKey: ReturnType<typeof vi.fn>;
        getDateTranslationParams: ReturnType<typeof vi.fn>;
    };
    let courseNotificationSettingServiceMock: {
        getSettingInfo: ReturnType<typeof vi.fn>;
        setSettingPreset: ReturnType<typeof vi.fn>;
        setSettingSpecification: ReturnType<typeof vi.fn>;
    };
    let activatedRouteMock: { params: { courseId: string } };

    const courseId = 123;

    const mockPresets: CourseNotificationSettingPreset[] = [
        {
            identifier: 'preset1',
            typeId: 1,
            presetMap: {
                newPostNotification: {
                    [CourseNotificationChannel.PUSH]: true,
                    [CourseNotificationChannel.EMAIL]: false,
                    [CourseNotificationChannel.WEBAPP]: true,
                },
            },
        },
        {
            identifier: 'preset2',
            typeId: 2,
            presetMap: {
                newPostNotification: {
                    [CourseNotificationChannel.PUSH]: false,
                    [CourseNotificationChannel.EMAIL]: true,
                    [CourseNotificationChannel.WEBAPP]: true,
                },
            },
        },
    ];

    const mockNotificationTypes: Record<number, string> = {
        1: 'newPostNotification',
    };

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

    const mockInfo: CourseNotificationInfo = {
        notificationTypes: mockNotificationTypes,
        presets: mockPresets,
    };

    beforeEach(async () => {
        courseNotificationServiceMock = {
            getInfo: vi.fn().mockReturnValue(of(mockInfo)),
            getIconFromType: vi.fn().mockReturnValue(undefined),
            getDateTranslationKey: vi.fn().mockReturnValue('artemisApp.courseNotification.temporal.now'),
            getDateTranslationParams: vi.fn().mockReturnValue({}),
        };

        courseNotificationSettingServiceMock = {
            getSettingInfo: vi.fn().mockReturnValue(of(mockSettingInfo)),
            setSettingPreset: vi.fn(),
            setSettingSpecification: vi.fn(),
        };

        activatedRouteMock = {
            params: { courseId: courseId.toString() },
        };

        await TestBed.configureTestingModule({
            imports: [NotificationSettingsComponent],
            providers: [
                { provide: CourseNotificationService, useValue: courseNotificationServiceMock },
                { provide: CourseNotificationSettingService, useValue: courseNotificationSettingServiceMock },
                { provide: ActivatedRoute, useValue: activatedRouteMock },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .overrideComponent(NotificationSettingsComponent, {
                remove: { imports: [FaIconComponent, TranslateDirective, CourseNotificationPresetPickerComponent, CourseNotificationSettingSpecificationCardComponent] },
                add: {
                    imports: [
                        MockComponent(FaIconComponent),
                        MockDirective(TranslateDirective),
                        MockComponent(CourseNotificationPresetPickerComponent),
                        MockComponent(CourseNotificationSettingSpecificationCardComponent),
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(NotificationSettingsComponent);
        component = fixture.componentInstance;

        component['courseId'] = courseId;

        component.onCourseIdAvailable();

        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should fetch setting info and notification info on initialization', () => {
        expect(courseNotificationSettingServiceMock.getSettingInfo).toHaveBeenCalledWith(courseId, true);
        expect(courseNotificationServiceMock.getInfo).toHaveBeenCalled();
    });

    it('should initialize values after receiving both info responses', async () => {
        component['settingInfo'] = mockSettingInfo;
        component['info'] = mockInfo;
        component.initializeValues();

        expect(component['isLoading']()).toBe(false);
        expect(component['selectableSettingPresets']()).toEqual(mockPresets);
        expect(component['selectedSettingPreset']()).toEqual(mockPresets[0]);
        expect(component['notificationSpecifications']()).toHaveLength(1);
    });

    it('waits for undefined settings to load and renders custom zero with all false flags', () => {
        const settings = new BehaviorSubject<CourseNotificationSettingInfo | undefined>(undefined);
        const info = new Subject<CourseNotificationInfo>();
        courseNotificationSettingServiceMock.getSettingInfo.mockReturnValue(settings);
        courseNotificationServiceMock.getInfo.mockReturnValue(info);
        component['settingInfo'] = undefined;
        component['info'] = undefined;
        component['isLoading'].set(true);
        component.onCourseIdAvailable();
        expect(courseNotificationSettingServiceMock.getSettingInfo).toHaveBeenCalledWith(courseId, true);
        info.next(mockInfo);
        expect(component['isLoading']()).toBe(true);
        const flags = { EMAIL: false, PUSH: false, WEBAPP: false };
        settings.next({ selectedPreset: 0, notificationTypeChannels: { '1': flags } });
        expect(component['isLoading']()).toBe(false);
        expect(component['selectedSettingPreset']()).toBeUndefined();
        expect(component['notificationSpecifications']()[0].channelSetting).toEqual(flags);
    });

    describe('settings that apply without a chosen preset (an unconfigured test account)', () => {
        // The server reports the custom preset with the channels that apply: the default preset's, but without e-mail.
        const effectiveSettingInfo: CourseNotificationSettingInfo = {
            selectedPreset: 0,
            notificationTypeChannels: {
                '1': {
                    [CourseNotificationChannel.PUSH]: true,
                    [CourseNotificationChannel.EMAIL]: false,
                    [CourseNotificationChannel.WEBAPP]: true,
                },
            },
        };
        // A shared preset that would switch e-mail on, as the default preset does.
        const presetsWithEmail: CourseNotificationSettingPreset[] = [
            {
                identifier: 'default',
                typeId: 1,
                presetMap: {
                    newPostNotification: {
                        [CourseNotificationChannel.PUSH]: true,
                        [CourseNotificationChannel.EMAIL]: true,
                        [CourseNotificationChannel.WEBAPP]: true,
                    },
                },
            },
        ];

        beforeEach(() => {
            component['settingInfo'] = effectiveSettingInfo;
            component['info'] = { notificationTypes: mockNotificationTypes, presets: presetsWithEmail };
            component.initializeValues();
        });

        it('should show the channels that apply and no selected preset, where the preset would show e-mail', () => {
            expect(component['selectedSettingPreset']()).toBeUndefined();
            expect(component['notificationSpecifications']()).toHaveLength(1);
            expect(component['notificationSpecifications']()[0].channelSetting[CourseNotificationChannel.EMAIL]).toBe(false);
        });

        it('should customize from what is shown, without a preset to copy', () => {
            const specification = component['notificationSpecifications']()[0];
            const changed = new CourseNotificationSettingSpecification(specification.identifier, specification.typeId, {
                ...specification.channelSetting,
                [CourseNotificationChannel.PUSH]: false,
            });

            component.optionChanged(changed);

            expect(courseNotificationSettingServiceMock.setSettingSpecification).toHaveBeenCalledWith(courseId, changed, undefined);
            expect(changed.channelSetting[CourseNotificationChannel.EMAIL]).toBe(false);
        });
    });

    it('should update selected preset when presetSelected is called', () => {
        component['settingInfo'] = mockSettingInfo;
        component['info'] = mockInfo;
        component.initializeValues();

        component.presetSelected(2);

        expect(component['selectedSettingPreset']()).toEqual(mockPresets[1]);
        expect(courseNotificationSettingServiceMock.setSettingPreset).toHaveBeenCalledWith(courseId, 2, mockPresets[1]);
    });

    it('should handle option changes correctly', () => {
        component['settingInfo'] = mockSettingInfo;
        component['info'] = mockInfo;
        component.initializeValues();

        const mockSpecification = new CourseNotificationSettingSpecification('newPostNotification', 1, {
            [CourseNotificationChannel.PUSH]: false,
            [CourseNotificationChannel.EMAIL]: true,
            [CourseNotificationChannel.WEBAPP]: true,
        });

        component.optionChanged(mockSpecification);

        expect(component['selectedSettingPreset']()).toBeUndefined();
        expect(courseNotificationSettingServiceMock.setSettingSpecification).toHaveBeenCalledWith(courseId, mockSpecification, mockPresets[0]);
    });

    it('should render preset picker when not loading', () => {
        component['settingInfo'] = mockSettingInfo;
        component['info'] = mockInfo;
        component.initializeValues();
        component['isLoading'].set(false);
        fixture.detectChanges();

        const presetPicker = fixture.debugElement.query(By.directive(CourseNotificationPresetPickerComponent));
        expect(presetPicker).not.toBeNull();

        expect(presetPicker.componentInstance.availableCourseSettingPresets()).toEqual(mockPresets);
        expect(presetPicker.componentInstance.selectedCourseSettingPreset()).toEqual(mockPresets[0]);
    });

    it('should render specification cards when not loading', () => {
        component['settingInfo'] = mockSettingInfo;
        component['info'] = mockInfo;
        component.initializeValues();
        component['isLoading'].set(false);
        fixture.detectChanges();

        const specificationCards = fixture.debugElement.queryAll(By.directive(CourseNotificationSettingSpecificationCardComponent));
        expect(specificationCards).toHaveLength(1);

        expect(specificationCards[0].componentInstance.settingSpecification()).toEqual(component['notificationSpecifications']()[0]);
    });

    it('should correctly update specifications from notification map', () => {
        component['info'] = {
            notificationTypes: { 1: 'newPostNotification' },
            presets: [],
        };

        const testMap: CourseNotificationSettingsMap = {
            newPostNotification: {
                [CourseNotificationChannel.PUSH]: false,
                [CourseNotificationChannel.EMAIL]: true,
                [CourseNotificationChannel.WEBAPP]: false,
            },
        };

        component['updateSpecificationArrayByNotificationMap'](testMap, true);

        expect(component['notificationSpecifications']()).toHaveLength(1);
        expect(component['notificationSpecifications']()[0].identifier).toBe('newPostNotification');
        expect(component['notificationSpecifications']()[0].typeId).toBe(1);
        expect(component['notificationSpecifications']()[0].channelSetting).toEqual(testMap['newPostNotification']);

        const testMapWithIds: CourseNotificationSettingsMap = {
            '1': {
                [CourseNotificationChannel.PUSH]: true,
                [CourseNotificationChannel.EMAIL]: false,
                [CourseNotificationChannel.WEBAPP]: true,
            },
        };

        component['updateSpecificationArrayByNotificationMap'](testMapWithIds, false);

        expect(component['notificationSpecifications']()).toHaveLength(1);
        expect(component['notificationSpecifications']()[0].identifier).toBe('newPostNotification');
        expect(component['notificationSpecifications']()[0].typeId).toBe(1);
        expect(component['notificationSpecifications']()[0].channelSetting).toEqual(testMapWithIds['1']);
    });

    describe('instructor-only notification types', () => {
        const channelSetting = {
            [CourseNotificationChannel.PUSH]: false,
            [CourseNotificationChannel.EMAIL]: false,
            [CourseNotificationChannel.WEBAPP]: false,
        };
        const mapWithInstructorOnlyType: CourseNotificationSettingsMap = {
            newPostNotification: channelSetting,
            atlasCompetencyUpdateNotification: channelSetting,
        };

        beforeEach(() => {
            component['info'] = {
                notificationTypes: { 1: 'newPostNotification', 27: 'atlasCompetencyUpdateNotification' },
                presets: [],
            };
        });

        it('should hide instructor-only types from users below instructor', () => {
            const instructorCheck = vi.spyOn(TestBed.inject(AccountService), 'isAtLeastInstructorInCourseWithId').mockReturnValue(false);

            component['updateSpecificationArrayByNotificationMap'](mapWithInstructorOnlyType, true);

            expect(instructorCheck).toHaveBeenCalledWith(courseId);
            expect(component['notificationSpecifications']().map((specification) => specification.identifier)).toEqual(['newPostNotification']);
        });

        it('should show instructor-only types to instructors', () => {
            vi.spyOn(TestBed.inject(AccountService), 'isAtLeastInstructorInCourseWithId').mockReturnValue(true);

            component['updateSpecificationArrayByNotificationMap'](mapWithInstructorOnlyType, true);

            expect(component['notificationSpecifications']().map((specification) => specification.identifier)).toEqual([
                'newPostNotification',
                'atlasCompetencyUpdateNotification',
            ]);
        });
    });
});
