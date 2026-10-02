import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CodeEditorAiActionsComponent } from 'app/programming/manage/code-editor/ai-actions/code-editor-ai-actions.component';

describe('CodeEditorAiActionsComponent', () => {
    let fixture: ComponentFixture<CodeEditorAiActionsComponent>;
    let comp: CodeEditorAiActionsComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CodeEditorAiActionsComponent],
            providers: [provideRouter([]), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(CodeEditorAiActionsComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('adaptOffered', true);
        fixture.detectChanges();
    });

    afterEach(() => {
        comp.close();
        fixture.destroy();
        vi.restoreAllMocks();
    });

    const testId = (id: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
    const overlayTestId = (id: string): HTMLElement | null => document.querySelector(`[data-testid="${id}"]`);

    function openMenu(): void {
        testId('hyperion-ai-menu')!.click();
        fixture.detectChanges();
    }

    it('emits adaptRequested from the primary action while nothing blocks adaptation', () => {
        const adaptRequested = vi.fn();
        comp.adaptRequested.subscribe(adaptRequested);

        const adapt = testId('hyperion-adapt-with-feedback')!;
        expect(adapt.getAttribute('aria-disabled')).toBeNull();
        adapt.click();

        expect(adaptRequested).toHaveBeenCalledOnce();
    });

    it('soft-disables the primary action with the translated reason and swallows the click while blocked', () => {
        const adaptRequested = vi.fn();
        comp.adaptRequested.subscribe(adaptRequested);
        fixture.componentRef.setInput('adaptBlockedReason', 'artemisApp.hyperion.generation.blocker.released');
        fixture.detectChanges();

        const adapt = testId('hyperion-adapt-with-feedback')!;
        expect(adapt.getAttribute('aria-disabled')).toBe('true');
        expect(adapt.hasAttribute('disabled')).toBe(false);
        adapt.click();

        expect(adaptRequested).not.toHaveBeenCalled();
    });

    it('shows the selected feedback count on the primary action', () => {
        expect(testId('hyperion-adapt-selected-count')).toBeNull();

        fixture.componentRef.setInput('selectedFeedbackCount', 3);
        fixture.detectChanges();

        expect(testId('hyperion-adapt-selected-count')!.textContent).toContain('3');
    });

    it('reduces to the labelled menu trigger when adaptation is not offered', () => {
        fixture.componentRef.setInput('adaptOffered', false);
        fixture.detectChanges();

        expect(testId('hyperion-adapt-with-feedback')).toBeNull();
        const trigger = testId('hyperion-ai-menu')!;
        expect(trigger.textContent).toContain('artemisApp.programmingExercise.artemisIntelligence.title');
        expect(trigger.getAttribute('aria-label')).toBeNull();
    });

    it('shows open consistency issues on the menu trigger and offers them in the menu', () => {
        const issuesToggled = vi.fn();
        comp.issuesToggled.subscribe(issuesToggled);
        expect(testId('hyperion-issue-count')).toBeNull();

        fixture.componentRef.setInput('issueCount', 2);
        fixture.detectChanges();
        expect(testId('hyperion-issue-count')!.textContent).toContain('2');

        openMenu();
        overlayTestId('hyperion-show-issues')!.click();
        fixture.detectChanges();

        expect(issuesToggled).toHaveBeenCalledOnce();
    });

    it('opens a menu with the refinement and consistency items and disables an item with its reason', () => {
        fixture.componentRef.setInput('consistencyBlockedReason', 'artemisApp.hyperion.generation.blocker.consistencyCheckBusy');
        fixture.detectChanges();

        openMenu();

        const refine = overlayTestId('hyperion-refine-problem-statement')!;
        const consistency = overlayTestId('hyperion-check-consistency')!;
        expect(refine.getAttribute('aria-disabled')).not.toBe('true');
        expect(refine.textContent).toContain('artemisApp.programmingExercise.problemStatement.refinementAssistance');
        expect(consistency.getAttribute('aria-disabled')).toBe('true');
        expect(consistency.textContent).toContain('artemisApp.hyperion.generation.blocker.consistencyCheckBusy');
        expect(overlayTestId('hyperion-show-issues')).toBeNull();
    });

    it('emits consistencyRequested when the consistency item is chosen', () => {
        const consistencyRequested = vi.fn();
        comp.consistencyRequested.subscribe(consistencyRequested);

        openMenu();
        overlayTestId('hyperion-check-consistency')!.click();
        fixture.detectChanges();

        expect(consistencyRequested).toHaveBeenCalledOnce();
        expect(overlayTestId('hyperion-check-consistency')).toBeNull();
    });

    it('offers the generation page while a run needs attention', () => {
        expect(testId('hyperion-editor-open-generation')).toBeNull();

        fixture.componentRef.setInput('progressLink', ['/course-management', 1, 'programming-exercises', 42, 'generation']);
        fixture.componentRef.setInput('progressRunning', true);
        fixture.detectChanges();

        const link = testId('hyperion-editor-open-generation')!;
        expect(link.getAttribute('href')).toBe('/course-management/1/programming-exercises/42/generation');
        expect(link.textContent).toContain('artemisApp.hyperion.generation.actions.viewRun');
    });

    it('renders the cancel button only while a cancellable operation runs and emits cancelRequested', () => {
        const cancelRequested = vi.fn();
        comp.cancelRequested.subscribe(cancelRequested);
        expect(testId('hyperion-cancel-ai-operation')).toBeNull();

        fixture.componentRef.setInput('cancelAvailable', true);
        fixture.detectChanges();
        testId('hyperion-cancel-ai-operation')!.click();

        expect(cancelRequested).toHaveBeenCalledOnce();
    });

    it('opens the refinement prompt from the menu and submits a non-empty prompt', () => {
        const refineSubmitted = vi.fn();
        comp.refineSubmitted.subscribe(refineSubmitted);

        openMenu();
        overlayTestId('hyperion-refine-problem-statement')!.click();
        fixture.detectChanges();

        expect(overlayTestId('hyperion-refinement-panel')).not.toBeNull();
        expect(comp['promptSubmittable']()).toBe(false);
        expect((overlayTestId('hyperion-refine-submit') as HTMLButtonElement).disabled).toBe(true);

        comp.prompt.set('   ');
        fixture.detectChanges();
        expect(comp['promptSubmittable']()).toBe(false);
        comp['submitRefinement']();
        expect(refineSubmitted).not.toHaveBeenCalled();

        comp.prompt.set('Make the tasks more explicit');
        fixture.detectChanges();
        expect(comp['promptSubmittable']()).toBe(true);
        overlayTestId('hyperion-refine-submit')!.click();
        fixture.detectChanges();

        expect(refineSubmitted).toHaveBeenCalledOnce();
        expect(overlayTestId('hyperion-refinement-panel')).toBeNull();
        expect(comp.prompt()).toBe('Make the tasks more explicit');
    });

    it('labels the refinement as generation while the problem statement is still the template', () => {
        fixture.componentRef.setInput('problemStatementEmpty', true);
        fixture.detectChanges();

        openMenu();
        expect(overlayTestId('hyperion-refine-problem-statement')!.textContent).toContain('artemisApp.programmingExercise.problemStatement.generationAssistance');
        overlayTestId('hyperion-refine-problem-statement')!.click();
        fixture.detectChanges();

        expect(overlayTestId('hyperion-refine-submit')!.textContent).toContain('artemisApp.programmingExercise.problemStatement.generate');
    });

    it('keeps the prompt but closes the popover when close() is called', () => {
        comp['openRefinement']();
        fixture.detectChanges();
        comp.prompt.set('keep me');
        expect(overlayTestId('hyperion-refinement-panel')).not.toBeNull();

        comp.close();
        fixture.detectChanges();

        expect(overlayTestId('hyperion-refinement-panel')).toBeNull();
        expect(comp.prompt()).toBe('keep me');
    });
});
