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
    it('should list every inconsistency in the table layout and link each exercise to its course', () => {
        const linked = new ConsistencyCheckError();
        linked.programmingExercise = { id: 7, title: 'Linked exercise' } as ProgrammingExercise;
        linked.type = ErrorType.TEMPLATE_BUILD_PLAN_MISSING;
        const second = new ConsistencyCheckError();
        second.programmingExercise = { id: 8, title: 'Second exercise' } as ProgrammingExercise;
        second.type = ErrorType.SOLUTION_BUILD_PLAN_MISSING;
        vi.spyOn(service, 'checkConsistencyForProgrammingExercise')
            .mockReturnValueOnce(of([linked]))
            .mockReturnValueOnce(of([second]));

        open(programmingExercises);

        const layout = document.querySelector('tumaet-ui-table-layout')!;
        const rows = layout.querySelectorAll('tbody tr');
        expect(layout.querySelector('table')).not.toBeNull();
        expect(rows).toHaveLength(2);
        expect(rows[0].textContent).toContain('Linked exercise');
        expect(rows[0].querySelector('a')!.getAttribute('href')).toBe('/course-management/123/programming-exercises/7');
        expect(rows[1].textContent).toContain('Second exercise');
        expect(rows[1].querySelector('a')!.getAttribute('href')).toBe('/course-management/123/programming-exercises/8');
    });

    it('should not render the table when no inconsistencies were found', () => {
        vi.spyOn(service, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([]));

        open([programmingExercise]);

        expect(document.querySelector('tumaet-ui-table-layout')).toBeNull();
    });
});
