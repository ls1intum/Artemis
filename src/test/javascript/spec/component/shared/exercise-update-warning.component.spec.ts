import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ExerciseUpdateWarningComponent } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.component';
import { MockDirective, MockProvider, MockPipe } from 'ng-mocks';
import { NgModel } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

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
        jest.clearAllMocks();
    });

    it('should trigger saveExerciseWithoutReevaluation once', () => {
        const emitSpy = jest.spyOn(comp.confirmed, 'emit');
        const saveExerciseWithoutReevaluation = jest.spyOn(comp, 'saveExerciseWithoutReevaluation');

        const button = fixture.debugElement.nativeElement.querySelector('#save-button');
        button.click();

        fixture.detectChanges();

        expect(saveExerciseWithoutReevaluation).toHaveBeenCalledTimes(1);
        expect(emitSpy).toHaveBeenCalled();
    });

    it('should trigger reEvaluateExercise once', () => {
        const emitSpy = jest.spyOn(comp.reEvaluated, 'emit');
        const reEvaluateExercise = jest.spyOn(comp, 'reEvaluateExercise');

        const button = fixture.debugElement.nativeElement.querySelector('#reevaluate-button');
        button.click();

        fixture.detectChanges();

        expect(reEvaluateExercise).toHaveBeenCalledTimes(1);
        expect(emitSpy).toHaveBeenCalled();
    });

    it('should trigger clear once', () => {
        const clear = jest.spyOn(comp, 'clear');

        const button = fixture.debugElement.nativeElement.querySelector('#cancel-button');
        button.click();

        fixture.detectChanges();

        expect(clear).toHaveBeenCalledTimes(1);
    });

    it('should toggle deleteFeedback', () => {
        comp.toggleDeleteFeedback();

        fixture.detectChanges();

        expect(comp.deleteFeedback).toBe(true);
    });
});
