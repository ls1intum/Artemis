import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgModel } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { ExerciseUpdateWarningComponent } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';

describe('Exercise Update Warning Component Tests', () => {
    let fixture: ComponentFixture<ExerciseUpdateWarningComponent>;
    let comp: ExerciseUpdateWarningComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ExerciseUpdateWarningComponent, MockDirective(NgModel), MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(TranslateService)],
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

    it('should trigger saveExerciseWithoutReevaluation once', () => {
        const emitSpy = jest.spyOn(comp.confirmed, 'emit');
        const saveExerciseWithoutReevaluationSpy = jest.spyOn(comp, 'saveExerciseWithoutReevaluation');

        const button = fixture.debugElement.nativeElement.querySelector('#save-button');
        button.click();

        fixture.detectChanges();

        expect(saveExerciseWithoutReevaluationSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledOnce();
    });

    it('should trigger reEvaluateExercise once', () => {
        const emitSpy = jest.spyOn(comp.reEvaluated, 'emit');
        const reEvaluateExerciseSpy = jest.spyOn(comp, 'reEvaluateExercise');

        const button = fixture.debugElement.nativeElement.querySelector('#reevaluate-button');
        button.click();

        fixture.detectChanges();

        expect(reEvaluateExerciseSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledOnce();
    });

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
