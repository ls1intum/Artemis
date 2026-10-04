import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ConsistencyCheckError, ErrorType } from 'app/programming/shared/entities/consistency-check-result.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { of } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { provideRouter } from '@angular/router';
import { ConsistencyCheckComponent } from 'app/programming/manage/consistency-check/consistency-check.component';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ConsistencyCheckComponent', () => {
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

    /** Opens the modal for the given exercises (the check runs when `visible` flips true, as it does at runtime). */
    function open(exercisesToCheck: ProgrammingExercise[]) {
        fixture = TestBed.createComponent(ConsistencyCheckComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exercisesToCheck', exercisesToCheck);
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
    }

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ConsistencyCheckComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, MockProvider(AlertService), MockProvider(ConsistencyCheckService), provideRouter([])],
        });
        service = TestBed.inject(ConsistencyCheckService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should call checks for single programming exercise', () => {
        const checkConsistencyForProgrammingExerciseStub = vi.spyOn(service, 'checkConsistencyForProgrammingExercise').mockReturnValue(of(consistencyErrors));

        open([programmingExercise]);

        expect(checkConsistencyForProgrammingExerciseStub).toHaveBeenCalledOnce();
        expect(component.inconsistencies()).toEqual(consistencyErrors);
    });

    it('should call checks for multiple programming exercises', () => {
        const checkConsistencyForProgrammingExerciseStub = vi.spyOn(service, 'checkConsistencyForProgrammingExercise').mockReturnValue(of(consistencyErrors));

        open(programmingExercises);

        expect(checkConsistencyForProgrammingExerciseStub).toHaveBeenCalledTimes(2);
        expect(component.inconsistencies()).toEqual(consistencyErrors.concat(consistencyErrors));
    });

    it('should close the dialog', () => {
        vi.spyOn(service, 'checkConsistencyForProgrammingExercise').mockReturnValue(of(consistencyErrors));
        open([programmingExercise]);

        component.closeModal();

        expect(component.visible()).toBe(false);
    });
});
