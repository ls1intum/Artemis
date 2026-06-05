import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ConsistencyCheckError, ErrorType } from 'app/programming/shared/entities/consistency-check-result.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { of } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { provideRouter } from '@angular/router';
import { ConsistencyCheckComponent } from 'app/programming/manage/consistency-check/consistency-check.component';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ConsistencyCheckComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ConsistencyCheckComponent;
    let fixture: ComponentFixture<ConsistencyCheckComponent>;
    let service: ConsistencyCheckService;

    const course = { id: 123, exercises: [] } as Course;
    const programmingExercise = new ProgrammingExercise(course, undefined);
    programmingExercise.id = 456;
    const programmingExercise2 = new ProgrammingExercise(course, undefined);
    programmingExercise.id = 567;
    const error1 = new ConsistencyCheckError();
    error1.programmingExercise = programmingExercise;
    error1.type = ErrorType.TEMPLATE_BUILD_PLAN_MISSING;
    const error2 = new ConsistencyCheckError();
    error2.programmingExercise = programmingExercise;
    error2.type = ErrorType.SOLUTION_BUILD_PLAN_MISSING;

    const consistencyErrors = [error1, error2];
    const programmingExercises = [programmingExercise, programmingExercise2];

    const dialogConfig: DynamicDialogConfig = { data: { exercisesToCheck: [] } };

    /**
     * Creates the component after the dialog config data has been set so the component
     * picks up the exercises to check via the PrimeNG DynamicDialogConfig (as it does at runtime).
     */
    function createComponent() {
        fixture = TestBed.createComponent(ConsistencyCheckComponent);
        component = fixture.componentInstance;
    }

    beforeEach(() => {
        dialogConfig.data = { exercisesToCheck: [] };
        TestBed.configureTestingModule({
            imports: [ConsistencyCheckComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DynamicDialogConfig, useValue: dialogConfig },
                { provide: DynamicDialogRef, useValue: { close: vi.fn() } },
                MockProvider(AlertService),
                MockProvider(ConsistencyCheckService),
                provideRouter([]),
            ],
        });
        service = TestBed.inject(ConsistencyCheckService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should call checks for single programming exercise', () => {
        // GIVEN
        const checkConsistencyForProgrammingExerciseStub = vi.spyOn(service, 'checkConsistencyForProgrammingExercise').mockReturnValue(of(consistencyErrors));
        dialogConfig.data = { exercisesToCheck: Array.of(programmingExercise) };

        // WHEN
        createComponent();
        fixture.detectChanges();

        // THEN
        expect(checkConsistencyForProgrammingExerciseStub).toHaveBeenCalledOnce();
        expect(component.inconsistencies).toEqual(consistencyErrors);
    });

    it('should call checks for multiple programming exercises', () => {
        // GIVEN
        const checkConsistencyForProgrammingExerciseStub = vi.spyOn(service, 'checkConsistencyForProgrammingExercise').mockReturnValue(of(consistencyErrors));
        dialogConfig.data = { exercisesToCheck: programmingExercises };

        // WHEN
        createComponent();
        fixture.detectChanges();

        // THEN
        expect(checkConsistencyForProgrammingExerciseStub).toHaveBeenCalledTimes(2);
        expect(component.inconsistencies).toEqual(consistencyErrors.concat(consistencyErrors));
    });

    it('should close the dialog', () => {
        // GIVEN
        vi.spyOn(service, 'checkConsistencyForProgrammingExercise').mockReturnValue(of(consistencyErrors));
        const dialogRef = TestBed.inject(DynamicDialogRef);
        createComponent();
        fixture.detectChanges();

        // WHEN
        component.closeModal();

        // THEN
        expect(dialogRef.close).toHaveBeenCalledOnce();
    });
});
