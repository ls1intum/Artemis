import { NgStyle } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { describe, expect, it, vi } from 'vitest';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExerciseTimelineStubComponent } from 'test/helpers/stubs/exercise/exercise-timeline-stub.component';
import { ExerciseUpdateTimelineComponent } from './exercise-update-timeline.component';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';

describe('ExerciseUpdateTimelineComponent', () => {
    let component: ExerciseUpdateTimelineComponent;
    let fixture: ComponentFixture<ExerciseUpdateTimelineComponent>;

    const toggle = () => fixture.debugElement.query(By.css('[data-testid="example-solution-publication-toggle"]')).nativeElement as HTMLInputElement;
    const hint = () => fixture.debugElement.query(By.css('[data-testid="example-solution-publication-hint"]'));
    const labelKeys = () => component.timelineItems().map((item) => item.labelStringKey);

    /** Creates the component with the inner (fully covered elsewhere) date list stubbed out. */
    async function createComponent(inputs: Record<string, unknown> = {}) {
        await TestBed.configureTestingModule({
            imports: [ExerciseUpdateTimelineComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(ExerciseUpdateTimelineComponent, {
                set: { imports: [FormsModule, NgStyle, TranslateDirective, ExerciseTimelineStubComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseUpdateTimelineComponent);
        component = fixture.componentInstance;
        for (const [key, value] of Object.entries(inputs)) {
            fixture.componentRef.setInput(key, value);
        }
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    }

    it('should list the four base dates and hide the example solution publication date by default', async () => {
        await createComponent({ hasExampleSolution: true });

        expect(labelKeys()).toEqual(['artemisApp.exercise.releaseDate', 'artemisApp.exercise.startDate', 'artemisApp.exercise.dueDate', 'artemisApp.exercise.assessmentDueDate']);
        expect(component.timelineItems()[3].otherRequiredItem).toBe(component.timelineItems()[2]);
        expect(component.timelineItems().every((item) => item.kind === 'optional')).toBe(true);
        expect(toggle().disabled).toBe(false);
        expect(hint()).toBeNull();
    });

    it('should append the example solution publication date once the opt-in is enabled', async () => {
        await createComponent({ hasExampleSolution: true });

        toggle().click();
        fixture.detectChanges();

        expect(labelKeys()).toEqual([
            'artemisApp.exercise.releaseDate',
            'artemisApp.exercise.startDate',
            'artemisApp.exercise.dueDate',
            'artemisApp.exercise.assessmentDueDate',
            'artemisApp.exercise.exampleSolutionPublicationDate',
        ]);
        expect(component.timelineItems()[4].date).toBe(component.exampleSolutionPublicationDate);
    });

    it('should pre-enable the opt-in for an exercise that already has a publication date', async () => {
        await createComponent({ hasExampleSolution: true, exampleSolutionPublicationDate: dayjs() });

        expect(component.isExampleSolutionPublicationDateVisible()).toBe(true);
        expect(toggle().checked).toBe(true);
        expect(labelKeys()).toContain('artemisApp.exercise.exampleSolutionPublicationDate');
    });

    it('should clear the date when the opt-in is switched off', async () => {
        await createComponent({ hasExampleSolution: true, exampleSolutionPublicationDate: dayjs() });
        const changes = vi.fn();
        component.exampleSolutionPublicationDate.subscribe(changes);

        toggle().click();
        fixture.detectChanges();

        expect(component.isExampleSolutionPublicationDateVisible()).toBe(false);
        expect(component.exampleSolutionPublicationDate()).toBeUndefined();
        expect(changes).toHaveBeenCalledWith(undefined);
        expect(labelKeys()).not.toContain('artemisApp.exercise.exampleSolutionPublicationDate');
    });

    it('should disable the opt-in with an explanation while the exercise has no example solution', async () => {
        await createComponent({ hasExampleSolution: false });

        expect(toggle().disabled).toBe(true);
        expect(component.exampleSolutionPublicationHintKey()).toBe('artemisApp.exercise.exampleSolutionPublicationDateRequiresExampleSolution');
        expect(hint()).not.toBeNull();
    });

    it('should expose a stored publication date for correction when the exercise has no example solution', async () => {
        const stored = dayjs();
        await createComponent({ hasExampleSolution: false, exampleSolutionPublicationDate: stored });

        expect(component.isExampleSolutionPublicationDateVisible()).toBe(true);
        expect(component.exampleSolutionPublicationDate()).toBe(stored);
        expect(labelKeys()).toContain('artemisApp.exercise.exampleSolutionPublicationDate');

        const corrected = stored.add(1, 'day');
        component.exampleSolutionPublicationDate.set(corrected);
        expect(component.exampleSolutionPublicationDate()).toBe(corrected);
    });

    it('should show a preserved publication date when it becomes configurable', async () => {
        const stored = dayjs();
        await createComponent({ hasExampleSolution: false, exampleSolutionPublicationDate: stored });

        fixture.componentRef.setInput('hasExampleSolution', true);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.isExampleSolutionPublicationDateVisible()).toBe(true);
        expect(component.exampleSolutionPublicationDate()).toBe(stored);
        expect(toggle().checked).toBe(true);
        expect(labelKeys()).toContain('artemisApp.exercise.exampleSolutionPublicationDate');
    });

    it('should disable the opt-in on import and say why', async () => {
        await createComponent({ hasExampleSolution: true, isImport: true });

        expect(toggle().disabled).toBe(true);
        expect(component.exampleSolutionPublicationHintKey()).toBe('artemisApp.exercise.exampleSolutionPublicationDateImportInfo');
        expect(hint()).not.toBeNull();
    });

    it('should pass the variant lock down to the inner timeline', async () => {
        await createComponent({ hasExampleSolution: true, lockedToGroup: true });
        const innerTimeline = fixture.debugElement.query(By.directive(ExerciseTimelineStubComponent)).componentInstance as ExerciseTimelineStubComponent;

        expect(innerTimeline.lockedToGroup()).toBe(true);
    });

    it('should surface a locked click from the inner timeline', async () => {
        await createComponent({ hasExampleSolution: true, lockedToGroup: true });
        const emitSpy = vi.spyOn(component.lockedClick, 'emit');
        const innerTimeline = fixture.debugElement.query(By.directive(ExerciseTimelineStubComponent)).componentInstance as ExerciseTimelineStubComponent;

        innerTimeline.lockedClick.emit();

        expect(emitSpy).toHaveBeenCalledOnce();
    });

    it('should disable the opt-in while the variant group governs the dates', async () => {
        await createComponent({ hasExampleSolution: true, lockedToGroup: true });

        expect(toggle().disabled).toBe(true);
        expect(component.exampleSolutionPublicationHintKey()).toBe('artemisApp.exercise.exampleSolutionPublicationDateLockedToGroup');
        expect(hint()).not.toBeNull();
        expect(labelKeys()).not.toContain('artemisApp.exercise.exampleSolutionPublicationDate');
    });

    it('should forward the status of the inner timeline', async () => {
        await createComponent({ hasExampleSolution: true });
        const emitSpy = vi.spyOn(component.timelineStatus, 'emit');
        const innerTimeline = fixture.debugElement.query(By.directive(ExerciseTimelineStubComponent)).componentInstance as ExerciseTimelineStubComponent;

        const timelineStatus = { valid: false, empty: true };
        innerTimeline.timelineStatusChange.emit(timelineStatus);

        expect(emitSpy).toHaveBeenCalledExactlyOnceWith(timelineStatus);
    });
    it('should require a scored exercise to publish its example solution after the due date', async () => {
        await createComponent({ hasExampleSolution: true, exampleSolutionPublicationDate: dayjs() });

        const items = component.timelineItems();
        const exampleSolutionItem = items[4];
        const labels = items.map((item) => item.labelStringKey);

        expect(exampleSolutionItem.labelStringKey).toBe('artemisApp.exercise.exampleSolutionPublicationDate');
        expect(exampleSolutionItem.orderCheckAgainst?.map((item) => item.labelStringKey)).toEqual([
            'artemisApp.exercise.releaseDate',
            'artemisApp.exercise.startDate',
            'artemisApp.exercise.dueDate',
        ]);
        expect(exampleSolutionItem.orderCheckAgainst).toEqual([
            items[labels.indexOf('artemisApp.exercise.releaseDate')],
            items[labels.indexOf('artemisApp.exercise.startDate')],
            items[labels.indexOf('artemisApp.exercise.dueDate')],
        ]);
    });

    it('should allow an unscored exercise to publish its example solution before the due date', async () => {
        await createComponent({ hasExampleSolution: true, exampleSolutionPublicationDate: dayjs(), includedInOverallScore: IncludedInOverallScore.NOT_INCLUDED });

        expect(component.timelineItems()[4].orderCheckAgainst).toEqual([component.timelineItems()[0], component.timelineItems()[1]]);
    });

    it('should clear the publication date when an existing example solution is removed', async () => {
        await createComponent({ hasExampleSolution: true, exampleSolutionPublicationDate: dayjs() });

        fixture.componentRef.setInput('hasExampleSolution', false);
        fixture.detectChanges();

        expect(component.exampleSolutionPublicationDate()).toBeUndefined();
    });
});
