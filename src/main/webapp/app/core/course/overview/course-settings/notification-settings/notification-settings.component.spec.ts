import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NotificationSettingsComponent } from 'app/core/course/overview/course-settings/notification-settings/notification-settings.component';
import { CourseNotificationSettingService } from 'app/communication/course-notification/course-notification-setting.service';
import { CourseNotificationService } from 'app/communication/course-notification/course-notification.service';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { CourseNotificationSettingPreset } from 'app/communication/shared/entities/course-notification/course-notification-setting-preset';
import { CourseNotificationSettingInfo } from 'app/communication/shared/entities/course-notification/course-notification-setting-info';
import { CourseNotificationInfo } from 'app/communication/shared/entities/course-notification/course-notification-info';
import { CourseNotificationSettingSpecification } from 'app/communication/shared/entities/course-notification/course-notification-setting-specification';
import { CourseNotificationChannel } from 'app/communication/shared/entities/course-notification/course-notification-channel';
import { By } from '@angular/platform-browser';
import { MockComponent } from 'ng-mocks';
import { CourseNotificationPresetPickerComponent } from 'app/communication/course-notification/course-notification-preset-picker/course-notification-preset-picker.component';
import { CourseNotificationSettingSpecificationCardComponent } from 'app/communication/course-notification/course-notification-setting-specification-card/course-notification-setting-specification-card.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockDirective } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseNotificationSettingsMap } from 'app/communication/shared/entities/course-notification/course-notification-settings-map';

describe('NotificationSettingsComponent', () => {
    let component: NotificationSettingsComponent;
    let fixture: ComponentFixture<NotificationSettingsComponent>;
    let courseNotificationServiceMock: jest.Mocked<CourseNotificationService>;
    let courseNotificationSettingServiceMock: jest.Mocked<CourseNotificationSettingService>;
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
            getInfo: jest.fn().mockReturnValue(of(new HttpResponse({ body: mockInfo }))),
        } as unknown as jest.Mocked<CourseNotificationService>;

        courseNotificationSettingServiceMock = {
            getSettingInfo: jest.fn().mockReturnValue(of(mockSettingInfo)),
            setSettingPreset: jest.fn(),
            setSettingSpecification: jest.fn(),
        } as unknown as jest.Mocked<CourseNotificationSettingService>;

        activatedRouteMock = {
            params: { courseId: courseId.toString() },
        };

        await TestBed.configureTestingModule({
            imports: [
                NotificationSettingsComponent,
                MockDirective(TranslateDirective),
                FaIconComponent,
                MockComponent(CourseNotificationPresetPickerComponent),
                MockComponent(CourseNotificationSettingSpecificationCardComponent),
            ],
            providers: [
                { provide: CourseNotificationService, useValue: courseNotificationServiceMock },
                { provide: CourseNotificationSettingService, useValue: courseNotificationSettingServiceMock },
                { provide: ActivatedRoute, useValue: activatedRouteMock },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(NotificationSettingsComponent);
        component = fixture.componentInstance;

        component['courseId'] = courseId;

        component.onCourseIdAvailable();

        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should fetch setting info and notification info on initialization', () => {
        expect(courseNotificationSettingServiceMock.getSettingInfo).toHaveBeenCalledWith(courseId, true);
        expect(courseNotificationServiceMock.getInfo).toHaveBeenCalled();
    });

    it('should initialize values after receiving both info responses', fakeAsync(() => {
        component['settingInfo'] = mockSettingInfo;
        component['info'] = mockInfo;
        component.initializeValues();
        tick();

        expect(component['isLoading']).toBeFalse();
        expect(component['selectableSettingPresets']).toEqual(mockPresets);
        expect(component['selectedSettingPreset']).toEqual(mockPresets[0]);
        expect(component['notificationSpecifications']).toHaveLength(1);
    }));

    it('should update selected preset when presetSelected is called', () => {
        component['settingInfo'] = mockSettingInfo;
        component['info'] = mockInfo;
        component.initializeValues();

        component.presetSelected(2);

        expect(component['selectedSettingPreset']).toEqual(mockPresets[1]);
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

        expect(component['selectedSettingPreset']).toBeUndefined();
        expect(courseNotificationSettingServiceMock.setSettingSpecification).toHaveBeenCalledWith(courseId, mockSpecification, mockPresets[0]);
    });

    it('should render preset picker when not loading', () => {
        component['settingInfo'] = mockSettingInfo;
        component['info'] = mockInfo;
        component.initializeValues();
        component['isLoading'] = false;
        fixture.detectChanges();

        const presetPicker = fixture.debugElement.query(By.directive(CourseNotificationPresetPickerComponent));
        expect(presetPicker).not.toBeNull();

        expect(presetPicker.componentInstance.availableCourseSettingPresets).toEqual(mockPresets);
        expect(presetPicker.componentInstance.selectedCourseSettingPreset).toEqual(mockPresets[0]);
    });

    it('should render specification cards when not loading', () => {
        component['settingInfo'] = mockSettingInfo;
        component['info'] = mockInfo;
        component.initializeValues();
        component['isLoading'] = false;
        fixture.detectChanges();

        const specificationCards = fixture.debugElement.queryAll(By.directive(CourseNotificationSettingSpecificationCardComponent));
        expect(specificationCards).toHaveLength(1);

        expect(specificationCards[0].componentInstance.settingSpecification).toEqual(component['notificationSpecifications'][0]);
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

        expect(component['notificationSpecifications']).toHaveLength(1);
        expect(component['notificationSpecifications'][0].identifier).toBe('newPostNotification');
        expect(component['notificationSpecifications'][0].typeId).toBe(1);
        expect(component['notificationSpecifications'][0].channelSetting).toEqual(testMap['newPostNotification']);

        const testMapWithIds: CourseNotificationSettingsMap = {
            '1': {
                [CourseNotificationChannel.PUSH]: true,
                [CourseNotificationChannel.EMAIL]: false,
                [CourseNotificationChannel.WEBAPP]: true,
            },
        };

        component['updateSpecificationArrayByNotificationMap'](testMapWithIds, false);

        expect(component['notificationSpecifications']).toHaveLength(1);
        expect(component['notificationSpecifications'][0].identifier).toBe('newPostNotification');
        expect(component['notificationSpecifications'][0].typeId).toBe(1);
        expect(component['notificationSpecifications'][0].channelSetting).toEqual(testMapWithIds['1']);
    });
});
