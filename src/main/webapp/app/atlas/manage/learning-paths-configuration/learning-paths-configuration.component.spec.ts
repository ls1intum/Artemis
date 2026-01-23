import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LearningPathsConfigurationComponent } from 'app/atlas/manage/learning-paths-configuration/learning-paths-configuration.component';
import { LearningPathApiService } from 'app/atlas/shared/services/learning-path-api.service';
import { AlertService } from 'app/shared/service/alert.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { LearningPathsConfigurationDTO } from 'app/atlas/shared/entities/learning-path.model';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('LearningPathsConfigurationComponent', () => {
    setupTestBed({ zoneless: true });
    let component: LearningPathsConfigurationComponent;
    let fixture: ComponentFixture<LearningPathsConfigurationComponent>;
    let learningPathApiService: LearningPathApiService;
    let alertService: AlertService;
    let getLearningPathsConfigurationSpy: ReturnType<typeof vi.spyOn>;

    const courseId = 1;

    const learningPathsConfiguration = <LearningPathsConfigurationDTO>{
        includeAllGradedExercises: true,
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LearningPathsConfigurationComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                { provide: AlertService, useClass: MockAlertService },
            ],
        }).compileComponents();

        learningPathApiService = TestBed.inject(LearningPathApiService);
        alertService = TestBed.inject(AlertService);

        getLearningPathsConfigurationSpy = vi.spyOn(learningPathApiService, 'getLearningPathsConfiguration').mockResolvedValue(learningPathsConfiguration);

        fixture = TestBed.createComponent(LearningPathsConfigurationComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', courseId);
    });

    it('should load learning paths configuration', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const includeAllExercisesCheckBox = fixture.nativeElement.querySelector('#include-all-graded-exercises-checkbox');

        expect(includeAllExercisesCheckBox.checked).toEqual(learningPathsConfiguration.includeAllGradedExercises);
        expect(component.includeAllGradedExercisesEnabled()).toEqual(learningPathsConfiguration.includeAllGradedExercises);
        expect(getLearningPathsConfigurationSpy).toHaveBeenCalledExactlyOnceWith(courseId);
    });

    it('should show error on load learning paths configuration', async () => {
        const alertServiceErrorSpy = vi.spyOn(alertService, 'addAlert');
        getLearningPathsConfigurationSpy.mockRejectedValue(new Error('Error'));

        fixture.detectChanges();
        await fixture.whenStable();

        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });

    it('should set isLoading correctly', async () => {
        const isLoadingSpy = vi.spyOn(component.isConfigLoading, 'set');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should enable edit mode', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const includeAllExercisesCheckBox = fixture.nativeElement.querySelector('#include-all-graded-exercises-checkbox');
        expect(includeAllExercisesCheckBox.disabled).toBeTruthy();

        await enableEditMode();

        expect(includeAllExercisesCheckBox.disabled).toBeFalsy();

        const saveButton = fixture.nativeElement.querySelector('#save-learning-paths-configuration-button');
        expect(saveButton).not.toBeNull();
        expect(component.isEditMode()).toBeTruthy();
    });

    it('should toggle include all graded exercises', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        await enableEditMode();

        const includeAllExercisesCheckBox = fixture.nativeElement.querySelector('#include-all-graded-exercises-checkbox');
        includeAllExercisesCheckBox.click();

        fixture.detectChanges();
        await fixture.whenStable();

        expect(includeAllExercisesCheckBox.checked).toBe(!learningPathsConfiguration.includeAllGradedExercises);
        expect(component.includeAllGradedExercisesEnabled()).toBe(!learningPathsConfiguration.includeAllGradedExercises);
    });

    it('should save learning paths configuration', async () => {
        const updateLearningPathsConfigurationSpy = vi.spyOn(learningPathApiService, 'updateLearningPathsConfiguration').mockResolvedValue();
        const alertServiceSuccessSpy = vi.spyOn(alertService, 'success');

        fixture.detectChanges();
        await fixture.whenStable();

        await enableEditMode();

        const includeAllExercisesCheckBox = fixture.nativeElement.querySelector('#include-all-graded-exercises-checkbox');
        includeAllExercisesCheckBox.click();

        const saveButton = fixture.nativeElement.querySelector('#save-learning-paths-configuration-button');
        saveButton.click();

        fixture.detectChanges();
        await fixture.whenStable();

        expect(updateLearningPathsConfigurationSpy).toHaveBeenCalledExactlyOnceWith(courseId, {
            ...learningPathsConfiguration,
            includeAllGradedExercises: !learningPathsConfiguration.includeAllGradedExercises,
        });
        expect(alertServiceSuccessSpy).toHaveBeenCalledOnce();
        expect(component.isEditMode()).toBeFalsy();
    });

    async function enableEditMode(): Promise<void> {
        const editButton = fixture.nativeElement.querySelector('#edit-learning-paths-configuration-button');
        editButton.click();

        fixture.detectChanges();
        await fixture.whenStable();
    }
});
