import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { IrisSettingsUpdateComponent } from 'app/iris/manage/settings/iris-settings-update/iris-settings-update.component';
import { IrisCourseSettings, IrisExerciseSettings, IrisGlobalSettings, IrisSettings, IrisSettingsType } from 'app/iris/shared/entities/settings/iris-settings.model';
import { mockSettings, mockVariants } from './mock-settings';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { IrisCommonSubSettingsUpdateComponent } from 'app/iris/manage/settings/iris-settings-update/iris-common-sub-settings-update/iris-common-sub-settings-update.component';
import { ButtonComponent } from 'app/shared/components/button/button.component';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { firstValueFrom, of } from 'rxjs';
import { IrisCourseSettingsUpdateComponent } from 'app/iris/manage/settings/iris-course-settings-update/iris-course-settings-update.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockJhiTranslateDirective } from '../../../helpers/mocks/directive/mock-jhi-translate-directive.directive';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import {
    IrisChatSubSettings,
    IrisCompetencyGenerationSubSettings,
    IrisCourseChatSubSettings,
    IrisLectureChatSubSettings,
    IrisLectureIngestionSubSettings,
    IrisTextExerciseChatSubSettings,
} from 'app/iris/shared/entities/settings/iris-sub-settings.model';
import { AlertService } from 'app/shared/service/alert.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';

describe('IrisSettingsUpdateComponent', () => {
    let component: IrisSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisSettingsUpdateComponent>;
    let getVariantsSpy: jest.SpyInstance;
    let getGlobalSettingsSpy: jest.SpyInstance;
    let getCombinedCourseSettingsSpy: jest.SpyInstance;
    let getUncombinedCourseSettingsSpy: jest.SpyInstance;
    let getUncombinedExerciseSettingsSpy: jest.SpyInstance;
    let setGlobalSettingsSpy: jest.SpyInstance;
    let setCourseSettingsSpy: jest.SpyInstance;
    let setExerciseSettingsSpy: jest.SpyInstance;

    beforeEach(() => {
        const irisSettingsServiceMock = {
            getGlobalSettings: jest.fn().mockReturnValue(of({} as IrisGlobalSettings)),
            getCombinedCourseSettings: jest.fn().mockReturnValue(of({} as IrisCourseSettings)),
            getUncombinedCourseSettings: jest.fn().mockReturnValue(of({} as IrisCourseSettings)),
            getUncombinedExerciseSettings: jest.fn().mockReturnValue(of({} as IrisExerciseSettings)),
            setGlobalSettings: jest.fn().mockReturnValue(of(new HttpResponse({ body: {} as IrisGlobalSettings }))),
            setCourseSettings: jest.fn().mockReturnValue(of(new HttpResponse({ body: {} as IrisCourseSettings }))),
            setExerciseSettings: jest.fn().mockReturnValue(of(new HttpResponse({ body: {} as IrisExerciseSettings }))),
        };

        TestBed.configureTestingModule({
            imports: [MockJhiTranslateDirective, IrisCourseSettingsUpdateComponent, IrisSettingsUpdateComponent, IrisCommonSubSettingsUpdateComponent],
            declarations: [MockPipe(ArtemisTranslatePipe), MockComponent(ButtonComponent)],
            providers: [
                MockProvider(IrisSettingsService, irisSettingsServiceMock),
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(IrisSettingsUpdateComponent);
                component = fixture.componentInstance;
                const irisSettingsService = TestBed.inject(IrisSettingsService);
                getVariantsSpy = jest.spyOn(irisSettingsService, 'getVariantsForFeature').mockReturnValue(of(mockVariants()));

                getGlobalSettingsSpy = jest.spyOn(irisSettingsService, 'getGlobalSettings');
                getCombinedCourseSettingsSpy = jest.spyOn(irisSettingsService, 'getCombinedCourseSettings');
                getUncombinedCourseSettingsSpy = jest.spyOn(irisSettingsService, 'getUncombinedCourseSettings');
                getUncombinedExerciseSettingsSpy = jest.spyOn(irisSettingsService, 'getUncombinedExerciseSettings');

                setGlobalSettingsSpy = jest.spyOn(irisSettingsService, 'setGlobalSettings');
                setCourseSettingsSpy = jest.spyOn(irisSettingsService, 'setCourseSettings');
                setExerciseSettingsSpy = jest.spyOn(irisSettingsService, 'setExerciseSettings');
            });
    });

    it('should display the checkbox for lecture ingestion when settingsType is COURSE', fakeAsync(() => {
        component.irisSettings = mockSettings();
        component.settingsType = IrisSettingsType.COURSE;
        fixture.detectChanges();
        tick();
        fixture.detectChanges();
        const lectureIngestionElement = fixture.debugElement.query(By.css('jhi-iris-common-sub-settings-update'));
        const checkboxElement = fixture.debugElement.query(By.css('input[type="checkbox"]#autoLectureIngestion'));
        const labelElement = fixture.debugElement.query(By.css('label[for="autoLectureIngestion"]'));
        const globalSettingsElement = fixture.debugElement.query(By.css('jhi-iris-global-autoupdate-settings-update'));
        expect(globalSettingsElement).toBeFalsy();
        expect(lectureIngestionElement).not.toBeNull();
        expect(checkboxElement).toBeTruthy();
        expect(labelElement).toBeTruthy();
        expect(getVariantsSpy).toHaveBeenCalled();
    }));

    describe('fillEmptyIrisSubSettings', () => {
        it('should do nothing if irisSettings is undefined', () => {
            component.irisSettings = undefined;
            component.fillEmptyIrisSubSettings();
            expect(component.irisSettings).toBeUndefined();
        });

        it('should create each sub-setting if not defined', () => {
            component.irisSettings = {} as IrisSettings;

            component.fillEmptyIrisSubSettings();

            expect(component.irisSettings).toBeDefined();
            expect(component.irisSettings!.irisChatSettings).toBeInstanceOf(IrisChatSubSettings);
            expect(component.irisSettings!.irisTextExerciseChatSettings).toBeInstanceOf(IrisTextExerciseChatSubSettings);
            expect(component.irisSettings!.irisLectureChatSettings).toBeInstanceOf(IrisLectureChatSubSettings);
            expect(component.irisSettings!.irisCourseChatSettings).toBeInstanceOf(IrisCourseChatSubSettings);
            expect(component.irisSettings!.irisLectureIngestionSettings).toBeInstanceOf(IrisLectureIngestionSubSettings);
            expect(component.irisSettings!.irisCompetencyGenerationSettings).toBeInstanceOf(IrisCompetencyGenerationSubSettings);
        });

        it('should preserve existing sub-settings and only create missing ones', () => {
            const existingChatSettings = new IrisChatSubSettings();
            existingChatSettings.enabled = true;
            existingChatSettings.selectedVariant = 'existingVariant';

            const existingLectureIngestionSettings = new IrisLectureIngestionSubSettings();
            existingLectureIngestionSettings.autoIngestOnLectureAttachmentUpload = true;

            component.irisSettings = {
                irisChatSettings: existingChatSettings,
                irisLectureIngestionSettings: existingLectureIngestionSettings,
            } as IrisSettings;

            component.fillEmptyIrisSubSettings();

            expect(component.irisSettings!.irisChatSettings).toBe(existingChatSettings);
            expect(component.irisSettings!.irisChatSettings!.enabled).toBeTrue();
            expect(component.irisSettings!.irisChatSettings!.selectedVariant).toBe('existingVariant');

            expect(component.irisSettings!.irisLectureIngestionSettings).toBe(existingLectureIngestionSettings);
            expect(component.irisSettings!.irisLectureIngestionSettings!.autoIngestOnLectureAttachmentUpload).toBeTrue();

            expect(component.irisSettings!.irisTextExerciseChatSettings).toBeInstanceOf(IrisTextExerciseChatSubSettings);
            expect(component.irisSettings!.irisLectureChatSettings).toBeInstanceOf(IrisLectureChatSubSettings);
            expect(component.irisSettings!.irisCourseChatSettings).toBeInstanceOf(IrisCourseChatSubSettings);
            expect(component.irisSettings!.irisCompetencyGenerationSettings).toBeInstanceOf(IrisCompetencyGenerationSubSettings);
        });
    });

    describe('loadParentIrisSettingsObservable', () => {
        it('should call getGlobalSettings for COURSE', async () => {
            component.settingsType = IrisSettingsType.COURSE;

            const result = await firstValueFrom(component.loadParentIrisSettingsObservable());

            expect(getGlobalSettingsSpy).toHaveBeenCalledOnce();
            expect(result).toEqual({});
        });

        it('should call getCombinedCourseSettings for EXERCISE', async () => {
            component.settingsType = IrisSettingsType.EXERCISE;
            component.courseId = 10;

            const result = await firstValueFrom(component.loadParentIrisSettingsObservable());

            expect(getCombinedCourseSettingsSpy).toHaveBeenCalledOnce();
            expect(getCombinedCourseSettingsSpy).toHaveBeenCalledWith(10);
            expect(result).toEqual({});
        });
    });

    describe('loadIrisSettingsObservable', () => {
        it('should call getGlobalSettings for GLOBAL', async () => {
            component.settingsType = IrisSettingsType.GLOBAL;

            const result = await firstValueFrom(component.loadIrisSettingsObservable());

            expect(getGlobalSettingsSpy).toHaveBeenCalledOnce();
            expect(result).toEqual({});
        });

        it('should call getUncombinedCourseSettings for COURSE', async () => {
            component.settingsType = IrisSettingsType.COURSE;
            component.courseId = 20;

            const result = await firstValueFrom(component.loadIrisSettingsObservable());

            expect(getUncombinedCourseSettingsSpy).toHaveBeenCalledOnce();
            expect(getUncombinedCourseSettingsSpy).toHaveBeenCalledWith(20);
            expect(result).toEqual({});
        });

        it('should call getUncombinedExerciseSettings for EXERCISE', async () => {
            component.settingsType = IrisSettingsType.EXERCISE;
            component.exerciseId = 30;

            const result = await firstValueFrom(component.loadIrisSettingsObservable());

            expect(getUncombinedExerciseSettingsSpy).toHaveBeenCalledOnce();
            expect(getUncombinedExerciseSettingsSpy).toHaveBeenCalledWith(30);
            expect(result).toEqual({});
        });
    });

    describe('saveIrisSettingsObservable', () => {
        beforeEach(() => {
            component.irisSettings = {} as IrisSettings;
        });

        it('should call setGlobalSettings for GLOBAL', async () => {
            component.settingsType = IrisSettingsType.GLOBAL;

            const httpResponse = await firstValueFrom(component.saveIrisSettingsObservable());

            expect(setGlobalSettingsSpy).toHaveBeenCalledOnce();
            expect(setGlobalSettingsSpy).toHaveBeenCalledWith({});
            expect(httpResponse.body).toEqual({});
        });

        it('should call setCourseSettings for COURSE', async () => {
            component.settingsType = IrisSettingsType.COURSE;
            component.courseId = 40;

            const httpResponse = await firstValueFrom(component.saveIrisSettingsObservable());

            expect(setCourseSettingsSpy).toHaveBeenCalledOnce();
            expect(setCourseSettingsSpy).toHaveBeenCalledWith(40, {});
            expect(httpResponse.body).toEqual({});
        });

        it('should call setExerciseSettings for EXERCISE', async () => {
            component.settingsType = IrisSettingsType.EXERCISE;
            component.exerciseId = 50;

            const httpResponse = await firstValueFrom(component.saveIrisSettingsObservable());

            expect(setExerciseSettingsSpy).toHaveBeenCalledOnce();
            expect(setExerciseSettingsSpy).toHaveBeenCalledWith(50, {});
            expect(httpResponse.body).toEqual({});
        });
    });
});
