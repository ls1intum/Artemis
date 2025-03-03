import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseUpdateWarningComponent } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.component';
import { MockProvider } from 'ng-mocks';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('Exercise Update Warning Component Tests', () => {
    let fixture: ComponentFixture<ExerciseUpdateWarningComponent>;
    let comp: ExerciseUpdateWarningComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [MockProvider(NgbActiveModal), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseUpdateWarningComponent);
                comp = fixture.componentInstance;

                comp.deleteFeedback = false;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should trigger saveExerciseWithoutReevaluation once', fakeAsync(() => {
        const emitSpy = jest.spyOn(comp.confirmed, 'emit');
        const saveExerciseWithoutReevaluationSpy = jest.spyOn(comp, 'saveExerciseWithoutReevaluation');

        comp.creditChanged = true;
        fixture.detectChanges();

        const button = fixture.debugElement.nativeElement.querySelector('#no-reevaluate-button');
        button.click();

        fixture.detectChanges();

        expect(saveExerciseWithoutReevaluationSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledOnce();
    }));

    it('should trigger reEvaluateExercise once', fakeAsync(() => {
        const emitSpy = jest.spyOn(comp.reEvaluated, 'emit');
        const reEvaluateExerciseSpy = jest.spyOn(comp, 'reEvaluateExercise');

        comp.creditChanged = true;
        fixture.detectChanges();

        const button = fixture.debugElement.nativeElement.querySelector('#reevaluate-button');
        button.click();

        fixture.detectChanges();

        expect(reEvaluateExerciseSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledOnce();
    }));

    it('should trigger clear once', () => {
        const clearSpy = jest.spyOn(comp, 'clear');
        const cancelledEmitSpy = jest.spyOn(comp.canceled, 'emit');

        const button = fixture.debugElement.nativeElement.querySelector('#cancel-button');
        button.click();

        fixture.detectChanges();

        expect(clearSpy).toHaveBeenCalledOnce();
        expect(cancelledEmitSpy).toHaveBeenCalledOnce();
    });

    it('should toggle deleteFeedback', () => {
        comp.toggleDeleteFeedback();

        fixture.detectChanges();

        expect(comp.deleteFeedback).toBeTrue();
    });
});
