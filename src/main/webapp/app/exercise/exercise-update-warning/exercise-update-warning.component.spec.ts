import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Subject } from 'rxjs';
import { TumAetUiDialogRef } from '@tumaet/ui-angular';

import { ExerciseUpdateWarningComponent } from 'app/exercise/exercise-update-warning/exercise-update-warning.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('Exercise Update Warning Component Tests', () => {
    let fixture: ComponentFixture<ExerciseUpdateWarningComponent>;
    let comp: ExerciseUpdateWarningComponent;
    const dismissed = new Subject<unknown>();

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: TumAetUiDialogRef, useValue: { close: vi.fn(), dismissed } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseUpdateWarningComponent);
        comp = fixture.componentInstance;
        comp.deleteFeedback.set(false);
    });

    it('should trigger saveExerciseWithoutReevaluation once', () => {
        const emitSpy = vi.spyOn(comp.confirmed, 'next');
        const saveExerciseWithoutReevaluationSpy = vi.spyOn(comp, 'saveExerciseWithoutReevaluation');

        fixture.componentRef.setInput('creditChanged', true);
        fixture.detectChanges();

        const button = fixture.debugElement.nativeElement.querySelector('#no-reevaluate-button');
        button.click();

        fixture.detectChanges();

        expect(saveExerciseWithoutReevaluationSpy).toHaveBeenCalledTimes(1);
        expect(emitSpy).toHaveBeenCalledTimes(1);
    });

    it('should trigger reEvaluateExercise once', () => {
        const emitSpy = vi.spyOn(comp.reEvaluated, 'next');
        const reEvaluateExerciseSpy = vi.spyOn(comp, 'reEvaluateExercise');

        fixture.componentRef.setInput('creditChanged', true);
        fixture.detectChanges();

        const button = fixture.debugElement.nativeElement.querySelector('#reevaluate-button');
        button.click();

        fixture.detectChanges();

        expect(reEvaluateExerciseSpy).toHaveBeenCalledTimes(1);
        expect(emitSpy).toHaveBeenCalledTimes(1);
    });

    it('should trigger clear once', () => {
        const clearSpy = vi.spyOn(comp, 'clear');
        const cancelledEmitSpy = vi.spyOn(comp.canceled, 'next');

        fixture.detectChanges();
        const button = fixture.debugElement.nativeElement.querySelector('#cancel-button');
        button.click();

        fixture.detectChanges();

        expect(clearSpy).toHaveBeenCalledTimes(1);
        expect(cancelledEmitSpy).toHaveBeenCalledTimes(1);
    });

    it('should cancel a pending save when the shell is dismissed', () => {
        const canceledSpy = vi.spyOn(comp.canceled, 'next');
        dismissed.next('escape');
        expect(canceledSpy).toHaveBeenCalledOnce();
    });

    it('should toggle deleteFeedback', () => {
        comp.toggleDeleteFeedback();

        fixture.detectChanges();

        expect(comp.deleteFeedback()).toBe(true);
    });
});
