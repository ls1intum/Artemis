import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CodeEditorAiActionsComponent } from 'app/hyperion/code-editor/ai-actions/code-editor-ai-actions.component';

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

    it('offers every AI action in one Artemis Intelligence menu', () => {
        fixture.componentRef.setInput('variantOffered', true);
        fixture.componentRef.setInput('progressLink', ['/course-management', 1, 'programming-exercises', 42]);
        fixture.detectChanges();

        const trigger = testId('hyperion-ai-menu')!;
        expect(trigger.textContent).toContain('artemisApp.programmingExercise.artemisIntelligence.title');
        expect(fixture.nativeElement.querySelectorAll('[data-testid="code-editor-ai-action-buttons"] > button')).toHaveLength(1);
        expect(testId('hyperion-adapt-with-feedback')).toBeNull();

        openMenu();
        const items = [...document.querySelectorAll('[tumAetUiMenuItem], .tumaet-ui-menu-item')].map((item) => item.getAttribute('data-testid'));
        expect(items).toEqual([
            'hyperion-editor-open-generation',
            'hyperion-adapt-with-feedback',
            'hyperion-create-variant',
            'hyperion-refine-problem-statement',
            'hyperion-check-consistency',
        ]);
        expect(overlayTestId('hyperion-adapt-with-feedback')!.textContent).toContain('artemisApp.hyperion.generation.actions.adapt');
    });

    it('emits adaptRequested from the menu while the exercise can be adapted', () => {
        const adaptRequested = vi.fn();
        comp.adaptRequested.subscribe(adaptRequested);

        openMenu();
        const adapt = overlayTestId('hyperion-adapt-with-feedback')!;
        expect(adapt.getAttribute('aria-disabled')).not.toBe('true');
        adapt.click();

        expect(adaptRequested).toHaveBeenCalledOnce();
    });

    it('disables adaptation with its reason as a title instead of a line of text', () => {
        const adaptRequested = vi.fn();
        comp.adaptRequested.subscribe(adaptRequested);
        fixture.componentRef.setInput('adaptUnavailableReason', 'artemisApp.hyperion.generation.blocker.released');
        fixture.detectChanges();

        openMenu();
        const adapt = overlayTestId('hyperion-adapt-with-feedback')!;
        expect(adapt.getAttribute('aria-disabled')).toBe('true');
        expect(adapt.getAttribute('title')).toBe('artemisApp.hyperion.generation.blocker.released');
        expect(adapt.textContent).not.toContain('artemisApp.hyperion.generation.blocker.released');
        comp['requestAdapt']();

        expect(adaptRequested).not.toHaveBeenCalled();
    });

    it('shows the selected feedback count on the menu trigger', () => {
        expect(testId('hyperion-adapt-selected-count')).toBeNull();

        fixture.componentRef.setInput('selectedFeedbackCount', 3);
        fixture.detectChanges();

        expect(testId('hyperion-ai-menu')!.contains(testId('hyperion-adapt-selected-count'))).toBe(true);
        expect(testId('hyperion-adapt-selected-count')!.textContent).toContain('3');
    });

    it('leaves adaptation out of the menu when it is not offered', () => {
        fixture.componentRef.setInput('adaptOffered', false);
        fixture.detectChanges();

        openMenu();
        expect(overlayTestId('hyperion-adapt-with-feedback')).toBeNull();
        expect(overlayTestId('hyperion-refine-problem-statement')).not.toBeNull();
    });

    it('offers direct consistency issue navigation without opening the AI menu', () => {
        const issuesToggled = vi.fn();
        comp.issuesToggled.subscribe(issuesToggled);
        expect(testId('hyperion-issue-count')).toBeNull();

        fixture.componentRef.setInput('issueCount', 2);
        fixture.detectChanges();
        expect(testId('hyperion-issue-count')!.textContent).toContain('2');

        testId('hyperion-show-issues')!.click();
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
        expect(consistency.getAttribute('title')).toBe('artemisApp.hyperion.generation.blocker.consistencyCheckBusy');
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

    it('offers the run in progress in the menu and marks the trigger as running', () => {
        expect(testId('hyperion-editor-open-generation')).toBeNull();

        fixture.componentRef.setInput('progressLink', ['/course-management', 1, 'programming-exercises', 42, 'generation']);
        fixture.componentRef.setInput('progressRunning', true);
        fixture.detectChanges();
        expect(testId('hyperion-ai-menu')!.querySelector('jhi-hyperion-status')).not.toBeNull();

        const progressRequested = vi.fn();
        comp.progressRequested.subscribe(progressRequested);
        openMenu();
        const item = overlayTestId('hyperion-editor-open-generation')!;
        expect(item.textContent).toContain('artemisApp.hyperion.generation.actions.viewRun');
        item.click();
        expect(progressRequested).toHaveBeenCalledOnce();
    });

    it('keeps the trigger and count badges compact in the editor toolbar', () => {
        fixture.componentRef.setInput('issueCount', 1);
        fixture.componentRef.setInput('selectedFeedbackCount', 1);
        fixture.detectChanges();

        for (const id of ['hyperion-ai-menu', 'hyperion-show-issues']) {
            expect(testId(id)!.getAttribute('size')).toBe('small');
        }
        for (const id of ['hyperion-adapt-selected-count', 'hyperion-issue-count']) {
            expect(testId(id)!.getAttribute('size')).toBe('sm');
            expect(testId(id)!.classList.contains('flex')).toBe(true);
        }
    });

    it('offers variant creation in the menu and emits only when available', () => {
        const variantRequested = vi.fn();
        comp.variantRequested.subscribe(variantRequested);
        fixture.componentRef.setInput('variantOffered', true);
        fixture.detectChanges();
        openMenu();
        expect(overlayTestId('hyperion-create-variant')!.textContent).toContain('artemisApp.hyperion.generation.actions.createVariant');
        overlayTestId('hyperion-create-variant')!.click();
        expect(variantRequested).toHaveBeenCalledOnce();
    });

    it('shows why variant creation is blocked and prevents a programmatic request', () => {
        const variantRequested = vi.fn();
        comp.variantRequested.subscribe(variantRequested);
        fixture.componentRef.setInput('variantOffered', true);
        fixture.componentRef.setInput('variantBlockedReason', 'artemisApp.hyperion.generation.blocker.saveBeforeVariant');
        fixture.detectChanges();
        openMenu();
        expect(overlayTestId('hyperion-create-variant')!.getAttribute('aria-disabled')).toBe('true');
        expect(overlayTestId('hyperion-create-variant')!.getAttribute('title')).toBe('artemisApp.hyperion.generation.blocker.saveBeforeVariant');
        expect(overlayTestId('hyperion-create-variant')!.textContent).not.toContain('artemisApp.hyperion.generation.blocker.saveBeforeVariant');
        comp['requestVariant']();
        expect(variantRequested).not.toHaveBeenCalled();
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
