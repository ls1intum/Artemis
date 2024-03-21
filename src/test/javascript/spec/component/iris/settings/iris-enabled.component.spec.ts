import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { mockSettings } from './mock-settings';
import { RouterTestingModule } from '@angular/router/testing';
import { IrisSettings } from 'app/entities/iris/settings/iris-settings.model';
import { HttpResponse } from '@angular/common/http';
import { IrisEnabledComponent } from 'app/iris/settings/shared/iris-enabled.component';
import { TranslatePipeMock } from '../../../helpers/mocks/service/mock-translate.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { IrisSubSettingsType } from 'app/entities/iris/settings/iris-sub-settings.model';

describe('IrisEnabledComponent', () => {
    let comp: IrisEnabledComponent;
    let fixture: ComponentFixture<IrisEnabledComponent>;
    let irisSettingsService: IrisSettingsService;

    const course = new Course();
    course.id = 5;
    const exercise = new ProgrammingExercise(course, undefined);
    exercise.id = 7;
    const irisSettings = mockSettings();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule],
            declarations: [IrisEnabledComponent, TranslatePipeMock],
            providers: [MockProvider(IrisSettingsService)],
        })
            .compileComponents()
            .then(() => {
                irisSettingsService = TestBed.inject(IrisSettingsService);
            });
        fixture = TestBed.createComponent(IrisEnabledComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).toBeDefined();
    });

    it.each([IrisSubSettingsType.CHAT, IrisSubSettingsType.HESTIA, IrisSubSettingsType.CODE_EDITOR, IrisSubSettingsType.COMPETENCY_GENERATION])(
        'should load exercise',
        async (subSettingstype) => {
            const getExerciseSettingsSpy = jest.spyOn(irisSettingsService, 'getUncombinedProgrammingExerciseSettings').mockReturnValue(of(irisSettings));
            comp.exercise = exercise;
            comp.irisSubSettingsType = subSettingstype;
            fixture.detectChanges();
            expect(getExerciseSettingsSpy).toHaveBeenCalledOnce();
            await Promise.resolve();
            expect(comp.irisSettings).toBe(irisSettings);
            expect(comp.irisSubSettings).toBeDefined();
        },
    );

    it.each([IrisSubSettingsType.CHAT, IrisSubSettingsType.HESTIA, IrisSubSettingsType.CODE_EDITOR, IrisSubSettingsType.COMPETENCY_GENERATION])(
        'should load course',
        async (subSettingstype) => {
            const getExerciseSettingsSpy = jest.spyOn(irisSettingsService, 'getUncombinedCourseSettings').mockReturnValue(of(irisSettings));
            comp.course = course;
            comp.irisSubSettingsType = subSettingstype;
            fixture.detectChanges();
            expect(getExerciseSettingsSpy).toHaveBeenCalledOnce();
            await Promise.resolve();
            expect(comp.irisSettings).toBe(irisSettings);
            expect(comp.irisSubSettings).toBeDefined();
        },
    );

    it('should set exercise enabled', async () => {
        const setSettingsSpy = jest.spyOn(irisSettingsService, 'setProgrammingExerciseSettings').mockReturnValue(of(new HttpResponse({ body: null as any as IrisSettings })));
        comp.exercise = exercise;
        comp.irisSettings = irisSettings;
        comp.irisSubSettingsType = IrisSubSettingsType.CHAT;
        comp.irisSubSettings = irisSettings.irisChatSettings;

        comp.setEnabled(true);
        expect(setSettingsSpy).toHaveBeenCalledOnce();
        await Promise.resolve();
        expect(comp.irisSubSettings?.enabled).toBeTrue();
    });

    it('should set course enabled', async () => {
        const setSettingsSpy = jest.spyOn(irisSettingsService, 'setCourseSettings').mockReturnValue(of(new HttpResponse({ body: null as any as IrisSettings })));
        comp.course = course;
        comp.irisSettings = irisSettings;
        comp.irisSubSettingsType = IrisSubSettingsType.HESTIA;
        comp.irisSubSettings = irisSettings.irisHestiaSettings;

        comp.setEnabled(true);
        expect(setSettingsSpy).toHaveBeenCalledOnce();
        await Promise.resolve();
        expect(comp.irisSubSettings?.enabled).toBeTrue();
    });
});
