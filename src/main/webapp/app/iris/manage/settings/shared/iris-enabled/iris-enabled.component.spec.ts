import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { mockSettings } from 'test/helpers/mocks/iris/mock-settings';
import { IrisCourseSettings, IrisSettings, IrisSettingsType } from 'app/iris/shared/entities/settings/iris-settings.model';
import { HttpResponse } from '@angular/common/http';
import { IrisEnabledComponent } from 'app/iris/manage/settings/shared/iris-enabled/iris-enabled.component';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { IrisSubSettingsType } from 'app/iris/shared/entities/settings/iris-sub-settings.model';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { ComponentRef } from '@angular/core';

describe('IrisEnabledComponent', () => {
    let comp: IrisEnabledComponent;
    let componentRef: ComponentRef<IrisEnabledComponent>;
    let fixture: ComponentFixture<IrisEnabledComponent>;
    let irisSettingsService: IrisSettingsService;

    const course = new Course();
    course.id = 5;
    const exercise = new ProgrammingExercise(course, undefined);
    exercise.id = 7;
    const irisSettings = mockSettings();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [IrisEnabledComponent, TranslatePipeMock],
            providers: [provideRouter([]), MockProvider(IrisSettingsService), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                irisSettingsService = TestBed.inject(IrisSettingsService);
            });
        fixture = TestBed.createComponent(IrisEnabledComponent);
        comp = fixture.componentInstance;
        componentRef = fixture.componentRef;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).toBeDefined();
    });

    it.each([IrisSubSettingsType.CHAT, IrisSubSettingsType.COMPETENCY_GENERATION])('should load exercise', async (subSettingstype) => {
        const getExerciseSettingsSpy = jest.spyOn(irisSettingsService, 'getUncombinedExerciseSettings').mockReturnValue(of(irisSettings));
        componentRef.setInput('exercise', exercise);
        componentRef.setInput('irisSubSettingsType', subSettingstype);
        fixture.detectChanges();
        expect(getExerciseSettingsSpy).toHaveBeenCalledOnce();
        await Promise.resolve();
        expect(comp.irisSettings).toBe(irisSettings);
        expect(comp.irisSubSettings).toBeDefined();
    });

    it.each([IrisSubSettingsType.CHAT, IrisSubSettingsType.COMPETENCY_GENERATION, IrisSubSettingsType.LECTURE_INGESTION])('should load course', async (subSettingstype) => {
        const getExerciseSettingsSpy = jest.spyOn(irisSettingsService, 'getUncombinedCourseSettings').mockReturnValue(of(irisSettings));
        componentRef.setInput('course', course);
        componentRef.setInput('irisSubSettingsType', subSettingstype);
        fixture.detectChanges();
        expect(getExerciseSettingsSpy).toHaveBeenCalledOnce();
        await Promise.resolve();
        expect(comp.irisSettings).toBe(irisSettings);
        expect(comp.irisSubSettings).toBeDefined();
    });

    it('should set exercise enabled', async () => {
        const setSettingsSpy = jest.spyOn(irisSettingsService, 'setExerciseSettings').mockReturnValue(of(new HttpResponse({ body: null as any as IrisSettings })));
        componentRef.setInput('exercise', exercise);
        componentRef.setInput('irisSubSettingsType', IrisSubSettingsType.CHAT);
        comp.irisSettings = irisSettings;
        comp.irisSubSettings = irisSettings.irisChatSettings;

        comp.setEnabled(true);
        expect(setSettingsSpy).toHaveBeenCalledOnce();
        await Promise.resolve();
        expect(comp.irisSubSettings?.enabled).toBeTrue();
    });

    it('should set course enabled', async () => {
        const setSettingsSpy = jest.spyOn(irisSettingsService, 'setCourseSettings').mockReturnValue(of(new HttpResponse({ body: null as any as IrisSettings })));
        componentRef.setInput('course', course);
        comp.irisSettings = irisSettings;
        componentRef.setInput('irisSubSettingsType', IrisSubSettingsType.CHAT);
        comp.irisSubSettings = irisSettings.irisChatSettings;

        comp.setEnabled(true);
        expect(setSettingsSpy).toHaveBeenCalledOnce();
        await Promise.resolve();
        expect(comp.irisSubSettings?.enabled).toBeTrue();
    });
    it('should set all subsettings to enabled when IrisSubSettingsType is ALL and setEnabled is called with true', async () => {
        const setSettingsSpy = jest.spyOn(irisSettingsService, 'setCourseSettings').mockReturnValue(of(new HttpResponse({ body: null as any as IrisSettings })));
        jest.spyOn(irisSettingsService, 'getUncombinedCourseSettings').mockReturnValue(of({} as IrisCourseSettings));
        componentRef.setInput('course', course);
        componentRef.setInput('irisSubSettingsType', IrisSubSettingsType.ALL);
        comp.irisSettings = {
            ...irisSettings,
            irisChatSettings: { type: IrisSubSettingsType.CHAT, enabled: false },
            irisTextExerciseChatSettings: { type: IrisSubSettingsType.TEXT_EXERCISE_CHAT, enabled: false },
            irisCourseChatSettings: { type: IrisSubSettingsType.COURSE_CHAT, enabled: false },
            irisCompetencyGenerationSettings: { type: IrisSubSettingsType.COMPETENCY_GENERATION, enabled: false },
            irisLectureChatSettings: { type: IrisSubSettingsType.LECTURE, enabled: false },
            irisFaqIngestionSettings: { type: IrisSubSettingsType.FAQ_INGESTION, enabled: false, autoIngestOnFaqCreation: false },
            irisLectureIngestionSettings: { type: IrisSubSettingsType.LECTURE_INGESTION, enabled: false, autoIngestOnLectureAttachmentUpload: false },
        };
        fixture.detectChanges();

        comp.setEnabled(true);

        expect(setSettingsSpy).toHaveBeenCalledOnce();
        expect(comp.irisSettings.irisChatSettings?.enabled).toBeTrue();
        expect(comp.irisSettings.irisTextExerciseChatSettings?.enabled).toBeTrue();
        expect(comp.irisSettings.irisCourseChatSettings?.enabled).toBeTrue();
        expect(comp.irisSettings.irisCompetencyGenerationSettings?.enabled).toBeTrue();
        expect(comp.irisSettings.irisLectureChatSettings?.enabled).toBeTrue();
        expect(comp.irisSettings.irisFaqIngestionSettings?.enabled).toBeTrue();
        expect(comp.irisSettings.irisLectureIngestionSettings?.enabled).toBeTrue();
    });

    it('should set all subsettings to disabled when IrisSubSettingsType is ALL and setEnabled is called with false', async () => {
        const setSettingsSpy = jest.spyOn(irisSettingsService, 'setCourseSettings').mockReturnValue(of(new HttpResponse({ body: null as any as IrisSettings })));
        jest.spyOn(irisSettingsService, 'getUncombinedCourseSettings').mockReturnValue(of({} as IrisCourseSettings));
        componentRef.setInput('course', course);
        componentRef.setInput('irisSubSettingsType', IrisSubSettingsType.ALL);
        comp.irisSettings = {
            ...irisSettings,
            irisChatSettings: { type: IrisSubSettingsType.CHAT, enabled: true },
            irisTextExerciseChatSettings: { type: IrisSubSettingsType.TEXT_EXERCISE_CHAT, enabled: true },
            irisCourseChatSettings: { type: IrisSubSettingsType.COURSE_CHAT, enabled: true },
            irisCompetencyGenerationSettings: { type: IrisSubSettingsType.COMPETENCY_GENERATION, enabled: true },
            irisLectureChatSettings: { type: IrisSubSettingsType.LECTURE, enabled: true },
            irisFaqIngestionSettings: { type: IrisSubSettingsType.FAQ_INGESTION, enabled: true, autoIngestOnFaqCreation: false },
            irisLectureIngestionSettings: { type: IrisSubSettingsType.LECTURE_INGESTION, enabled: true, autoIngestOnLectureAttachmentUpload: false },
        };
        fixture.detectChanges();

        comp.setEnabled(false);

        expect(setSettingsSpy).toHaveBeenCalledOnce();
        expect(comp.irisSettings.irisChatSettings?.enabled).toBeFalse();
        expect(comp.irisSettings.irisTextExerciseChatSettings?.enabled).toBeFalse();
        expect(comp.irisSettings.irisCourseChatSettings?.enabled).toBeFalse();
        expect(comp.irisSettings.irisCompetencyGenerationSettings?.enabled).toBeFalse();
        expect(comp.irisSettings.irisLectureChatSettings?.enabled).toBeFalse();
        expect(comp.irisSettings.irisFaqIngestionSettings?.enabled).toBeFalse();
        expect(comp.irisSettings.irisLectureIngestionSettings?.enabled).toBeFalse();
        expect(comp.someButNotAllSettingsEnabled).toBeFalse();
    });

    it('should create new subsettings if they do not exist when IrisSubSettingsType is ALL and setEnabled is called', async () => {
        const setSettingsSpy = jest.spyOn(irisSettingsService, 'setCourseSettings').mockReturnValue(of(new HttpResponse({ body: null as any as IrisSettings })));
        jest.spyOn(irisSettingsService, 'getUncombinedCourseSettings').mockReturnValue(of({} as IrisCourseSettings));
        componentRef.setInput('course', course);
        componentRef.setInput('irisSubSettingsType', IrisSubSettingsType.ALL);
        comp.irisSettings = { type: IrisSettingsType.COURSE };
        fixture.detectChanges();

        comp.setEnabled(true);

        expect(setSettingsSpy).toHaveBeenCalledOnce();
        expect(comp.irisSettings!.irisChatSettings).toBeDefined();
        expect(comp.irisSettings!.irisTextExerciseChatSettings).toBeDefined();
        expect(comp.irisSettings!.irisCourseChatSettings).toBeDefined();
        expect(comp.irisSettings!.irisCompetencyGenerationSettings).toBeDefined();
        expect(comp.irisSettings!.irisLectureChatSettings).toBeDefined();
        expect(comp.irisSettings!.irisFaqIngestionSettings).toBeDefined();
        expect(comp.irisSettings!.irisLectureIngestionSettings).toBeDefined();
        expect(comp.irisSettings!.irisChatSettings?.enabled).toBeTrue();
        expect(comp.irisSettings!.irisTextExerciseChatSettings?.enabled).toBeTrue();
        expect(comp.irisSettings!.irisCourseChatSettings?.enabled).toBeTrue();
        expect(comp.irisSettings!.irisCompetencyGenerationSettings?.enabled).toBeTrue();
        expect(comp.irisSettings!.irisLectureChatSettings?.enabled).toBeTrue();
        expect(comp.irisSettings!.irisFaqIngestionSettings?.enabled).toBeTrue();
        expect(comp.irisSettings!.irisLectureIngestionSettings?.enabled).toBeTrue();
        expect(comp.someButNotAllSettingsEnabled).toBeFalse();
    });

    it('should set irisSubSettings.enabled to true and someButNotAllSettingsEnabled to true when some but not all sub-settings are enabled', async () => {
        jest.spyOn(irisSettingsService, 'getUncombinedCourseSettings').mockReturnValue(
            of({
                irisChatSettings: { type: IrisSubSettingsType.CHAT, enabled: true },
                irisTextExerciseChatSettings: { type: IrisSubSettingsType.TEXT_EXERCISE_CHAT, enabled: false },
                irisCourseChatSettings: { type: IrisSubSettingsType.COURSE_CHAT, enabled: true },
                irisCompetencyGenerationSettings: { type: IrisSubSettingsType.COMPETENCY_GENERATION, enabled: false },
                irisLectureChatSettings: { type: IrisSubSettingsType.LECTURE, enabled: false },
                irisFaqIngestionSettings: { type: IrisSubSettingsType.FAQ_INGESTION, enabled: false, autoIngestOnFaqCreation: false },
                irisLectureIngestionSettings: { type: IrisSubSettingsType.LECTURE_INGESTION, enabled: false, autoIngestOnLectureAttachmentUpload: false },
            } as IrisCourseSettings),
        );
        componentRef.setInput('course', course);
        componentRef.setInput('irisSubSettingsType', IrisSubSettingsType.ALL);
        fixture.detectChanges();

        expect(comp.irisSubSettings?.enabled).toBeTrue();
        expect(comp.someButNotAllSettingsEnabled).toBeTrue();
    });
});
