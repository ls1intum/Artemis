import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ExerciseUpdateWarningComponent } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.component';
import { MockDirective, MockProvider, MockPipe } from 'ng-mocks';
import { NgModel } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

chai.use(sinonChai);
const expect = chai.expect;

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

    afterEach(function () {
        sinon.restore();
    });

    it('should trigger saveExerciseWithoutReevaluation once', () => {
        const emitSpy = sinon.spy(comp.confirmed, 'emit');
        const saveExerciseWithoutReevaluation = sinon.spy(comp, 'saveExerciseWithoutReevaluation');

        const button = fixture.debugElement.nativeElement.querySelector('#save-button');
        button.click();

        fixture.detectChanges();

        expect(saveExerciseWithoutReevaluation).to.have.been.calledOnce;
        expect(emitSpy).to.have.been.called;
    });

    it('should trigger reEvaluateExercise once', () => {
        const emitSpy = sinon.spy(comp.reEvaluated, 'emit');
        const reEvaluateExercise = sinon.spy(comp, 'reEvaluateExercise');

        const button = fixture.debugElement.nativeElement.querySelector('#reevaluate-button');
        button.click();

        fixture.detectChanges();

        expect(reEvaluateExercise).to.have.been.calledOnce;
        expect(emitSpy).to.have.been.called;
    });

    it('should trigger clear once', () => {
        const clear = sinon.spy(comp, 'clear');

        const button = fixture.debugElement.nativeElement.querySelector('#cancel-button');
        button.click();

        fixture.detectChanges();

        expect(clear).to.have.been.calledOnce;
    });

    it('should toggle deleteFeedback', () => {
        comp.toggleDeleteFeedback();

        fixture.detectChanges();

        expect(comp.deleteFeedback).to.be.true;
    });
});
