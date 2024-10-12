import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IrisSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-settings-update.component';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { BehaviorSubject, of } from 'rxjs';
import { ButtonComponent } from 'app/shared/components/button.component';
import { IrisCommonSubSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-common-sub-settings-update/iris-common-sub-settings-update.component';
import { mockSettings } from './mock-settings';
import { IrisExerciseSettingsUpdateComponent } from 'app/iris/settings/iris-exercise-settings-update/iris-exercise-settings-update.component';
import { ActivatedRoute, Params, provideRouter } from '@angular/router';
import { NgModel } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { IrisSettings } from 'app/entities/iris/settings/iris-settings.model';
import { HttpResponse } from '@angular/common/http';

describe('IrisExerciseSettingsUpdateComponent Component', () => {
    let comp: IrisExerciseSettingsUpdateComponent;
    let fixture: ComponentFixture<IrisExerciseSettingsUpdateComponent>;
    let irisSettingsService: IrisSettingsService;
    const routeParamsSubject = new BehaviorSubject<Params>({ courseId: 1, exerciseId: 1 });
    const route = { parent: { params: routeParamsSubject.asObservable() } } as ActivatedRoute;
    let paramsSpy: jest.SpyInstance;
    let getSettingsSpy: jest.SpyInstance;
    let getParentSettingsSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                IrisExerciseSettingsUpdateComponent,
                IrisSettingsUpdateComponent,
                MockComponent(IrisCommonSubSettingsUpdateComponent),
                MockComponent(ButtonComponent),
                MockDirective(NgModel),
            ],
            providers: [provideRouter([]), MockProvider(IrisSettingsService), { provide: ActivatedRoute, useValue: route }],
        })
            .compileComponents()
            .then(() => {
                irisSettingsService = TestBed.inject(IrisSettingsService);

                // Setup
                routeParamsSubject.next({ courseId: 1, exerciseId: 2 });
                paramsSpy = jest.spyOn(route.parent!.params, 'subscribe');

                const irisSettings = mockSettings();
                getSettingsSpy = jest.spyOn(irisSettingsService, 'getUncombinedProgrammingExerciseSettings').mockReturnValue(of(irisSettings));
                getParentSettingsSpy = jest.spyOn(irisSettingsService, 'getCombinedCourseSettings').mockReturnValue(of(irisSettings));
            });
        fixture = TestBed.createComponent(IrisExerciseSettingsUpdateComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('Setup works correctly', () => {
        fixture.detectChanges();
        expect(paramsSpy).toHaveBeenCalledOnce();
        expect(comp.courseId).toBe(1);
        expect(comp.exerciseId).toBe(2);
        expect(comp.settingsUpdateComponent).toBeTruthy();
        expect(getSettingsSpy).toHaveBeenCalledWith(2);
        expect(getParentSettingsSpy).toHaveBeenCalledWith(1);

        expect(fixture.debugElement.queryAll(By.directive(IrisCommonSubSettingsUpdateComponent))).toHaveLength(1);
    });

    it('Can deactivate correctly', () => {
        fixture.detectChanges();
        expect(comp.canDeactivate()).toBeTrue();
        comp.settingsUpdateComponent!.isDirty = true;
        expect(comp.canDeactivate()).toBeFalse();
        comp.settingsUpdateComponent!.canDeactivateWarning = 'Warning';
        expect(comp.canDeactivateWarning).toBe('Warning');
    });

    it('Saves settings correctly', () => {
        fixture.detectChanges();
        const irisSettings = mockSettings();
        irisSettings.id = undefined;
        const irisSettingsSaved = mockSettings();
        const setSettingsSpy = jest.spyOn(irisSettingsService, 'setProgrammingExerciseSettings').mockReturnValue(of(new HttpResponse<IrisSettings>({ body: irisSettingsSaved })));
        comp.settingsUpdateComponent!.irisSettings = irisSettings;
        comp.settingsUpdateComponent!.saveIrisSettings();
        expect(setSettingsSpy).toHaveBeenCalledWith(2, irisSettings);
        expect(comp.settingsUpdateComponent!.irisSettings).toEqual(irisSettingsSaved);
    });
});
