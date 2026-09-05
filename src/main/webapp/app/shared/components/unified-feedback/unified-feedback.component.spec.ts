import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UnifiedFeedbackComponent } from './unified-feedback.component';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';
import {
    FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER,
    FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER,
    FEEDBACK_SUGGESTION_IDENTIFIER,
    Feedback,
} from 'app/assessment/shared/entities/feedback.model';
import { By } from '@angular/platform-browser';
import { FeedbackSuggestionBadgeComponent } from 'app/exercise/feedback/feedback-suggestion-badge/feedback-suggestion-badge.component';
import { vi } from 'vitest';
import { faMinus } from '@fortawesome/free-solid-svg-icons';

const CREDITS_STEP = 0.5;

describe('UnifiedFeedbackComponent', () => {
    let component: UnifiedFeedbackComponent;
    let fixture: ComponentFixture<UnifiedFeedbackComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UnifiedFeedbackComponent],
            providers: [provideTranslateService()],
        }).compileComponents();

        const translateService = TestBed.inject(TranslateService);
        translateService.setTranslation('en', {
            artemisApp: { assessment: { detail: { points: { one: '{{points}} Point', many: '{{points}} Points' } } } },
        });
        translateService.use('en');

        fixture = TestBed.createComponent(UnifiedFeedbackComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should have default values', () => {
        expect(component.feedbackContent()).toBe('');
        expect(component.points()).toBe(0);
        expect(component.type()).toBeUndefined();
        expect(component.title()).toBeUndefined();
        expect(component.reference()).toBeUndefined();
    });

    it('should have editable-mode defaults', () => {
        expect(component.editable()).toBe(false);
        expect(component.readOnly()).toBe(false);
        expect(component.feedbackTitle()).toBeUndefined();
        expect(component.feedbackDetail()).toBeUndefined();
        expect(component.feedbackCredits()).toBe(0);
    });

    it('should infer needs_revision type by default when points = 0', () => {
        expect(component.inferredType()).toBe('needs_revision');
        expect(component.inferredTitle()).toBe('artemisApp.feedback.type.feedback');
        expect(component.inferredAlertClass()).toBe('alert-primary');
    });

    it('should return correct alert class for default needs_revision type', () => {
        expect(component.inferredAlertClass()).toBe('alert-primary');
    });

    it('should infer correct type when points > 0', () => {
        fixture.componentRef.setInput('points', 5);
        fixture.detectChanges();
        expect(component.inferredType()).toBe('correct');
        expect(component.inferredAlertClass()).toBe('alert-success');
    });

    it('should infer non_compliant type when points < 0', () => {
        fixture.componentRef.setInput('points', -1);
        fixture.detectChanges();
        expect(component.inferredType()).toBe('non_compliant');
        expect(component.inferredAlertClass()).toBe('alert-danger');
    });

    it('should prefer explicit type over inferred from points', () => {
        fixture.componentRef.setInput('points', 0);
        fixture.componentRef.setInput('type', 'correct');
        fixture.detectChanges();
        expect(component.inferredType()).toBe('correct');
        expect(component.inferredAlertClass()).toBe('alert-success');
    });

    it('should use explicit title when provided', () => {
        fixture.componentRef.setInput('title', 'Explicit Title');
        fixture.detectChanges();
        expect(component.inferredTitle()).toBe('Explicit Title');
    });

    it('should use feedback.text as title only when detailText also exists', () => {
        fixture.componentRef.setInput('title', undefined);
        fixture.componentRef.setInput('feedback', { text: 'Title', detailText: 'Description' } as any);
        fixture.detectChanges();
        expect(component.inferredTitle()).toBe('Title');
    });

    it('should fall back to default title when feedback.text is plain text without detailText', () => {
        fixture.componentRef.setInput('title', undefined);
        fixture.componentRef.setInput('feedback', { text: 'Feedback Text' } as any);
        fixture.detectChanges();
        expect(component.inferredTitle()).toBe('artemisApp.feedback.type.feedback');
    });

    it('should infer title from assessmentsNames when feedback has referenceId and mapping exists', () => {
        fixture.componentRef.setInput('title', undefined);
        fixture.componentRef.setInput('points', 0);
        fixture.componentRef.setInput('feedback', { referenceId: 42 } as any);
        fixture.componentRef.setInput('assessmentsNames', { 42: { type: 'Model', name: 'Class Diagram' } } as any);
        fixture.detectChanges();
        expect(component.inferredTitle()).toBe('Model: Class Diagram');
    });

    it('should fall back to default title when no feedback text or mapping', () => {
        fixture.componentRef.setInput('title', undefined);
        fixture.componentRef.setInput('feedback', {} as any);
        fixture.componentRef.setInput('assessmentsNames', undefined as any);
        fixture.componentRef.setInput('points', 0);
        fixture.detectChanges();
        expect(component.inferredTitle()).toBe('artemisApp.feedback.type.feedback');
        fixture.componentRef.setInput('points', 2);
        fixture.detectChanges();
        expect(component.inferredTitle()).toBe('artemisApp.feedback.type.positive');
    });

    it('should use explicit reference when provided', () => {
        fixture.componentRef.setInput('reference', 'Explicit Ref');
        fixture.detectChanges();
        expect(component.inferredReference()).toBe('Explicit Ref');
    });

    it('should infer reference from assessmentsNames mapping', () => {
        fixture.componentRef.setInput('reference', undefined);
        fixture.componentRef.setInput('feedback', { referenceId: 7 } as any);
        fixture.componentRef.setInput('assessmentsNames', { 7: { type: 'Model', name: 'ER Diagram' } } as any);
        fixture.detectChanges();
        expect(component.inferredReference()).toBe('Model ER Diagram');
    });

    it('should infer reference from feedback.reference when mapping missing', () => {
        fixture.componentRef.setInput('reference', undefined);
        fixture.componentRef.setInput('assessmentsNames', undefined as any);
        fixture.componentRef.setInput('feedback', { reference: 'line 12' } as any);
        fixture.detectChanges();
        expect(component.inferredReference()).toBe('line 12');
    });

    it('should render reference section only when showReference and inferredReference are truthy', () => {
        fixture.componentRef.setInput('reference', 'Shown Ref');
        fixture.componentRef.setInput('showReference', true);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.unified-feedback-reference-text')?.textContent).toContain('Shown Ref');

        fixture.componentRef.setInput('showReference', false);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.unified-feedback-reference-text')).toBeNull();
    });

    it('should render points and feedback content', () => {
        fixture.componentRef.setInput('points', 3);
        fixture.componentRef.setInput('feedbackContent', '<p>Hello</p>');
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.unified-feedback-points')?.textContent).toContain('3');
        expect(fixture.nativeElement.querySelector('.unified-feedback-text')?.innerHTML).toContain('<p>Hello</p>');
    });

    it('should render the read-only points pill as a signed number without a "Point(s)" word, colored by sign', () => {
        fixture.componentRef.setInput('points', 3);
        fixture.detectChanges();
        let pill = fixture.nativeElement.querySelector('.unified-feedback-points') as HTMLElement;
        expect(pill.textContent?.trim()).toBe('+3');
        expect(pill.classList).toContain('unified-feedback-points--positive');

        fixture.componentRef.setInput('points', -2);
        fixture.detectChanges();
        pill = fixture.nativeElement.querySelector('.unified-feedback-points') as HTMLElement;
        expect(pill.textContent?.trim()).toBe('-2');
        expect(pill.classList).toContain('unified-feedback-points--negative');

        fixture.componentRef.setInput('points', 0);
        fixture.detectChanges();
        pill = fixture.nativeElement.querySelector('.unified-feedback-points') as HTMLElement;
        expect(pill.textContent?.trim()).toBe('0');
        expect(pill.classList).toContain('unified-feedback-points--neutral');
    });

    it('should wrap the type icon in a circular icon badge', () => {
        fixture.detectChanges();
        const badge = fixture.nativeElement.querySelector('.unified-feedback-icon-badge');
        expect(badge).toBeTruthy();
        expect(badge.querySelector('fa-icon')).toBeTruthy();
    });

    it('should strip the accepted-suggestion prefix from the Athena suggestion title', () => {
        fixture.componentRef.setInput('title', undefined);
        fixture.componentRef.setInput('feedback', { text: `${FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER}Missing null check` } as any);
        fixture.detectChanges();
        expect(component.inferredTitle()).toBe('Missing null check');
    });

    it('should fall back to default title when mapping is present but id missing', () => {
        fixture.componentRef.setInput('title', undefined);
        fixture.componentRef.setInput('points', 0);
        fixture.componentRef.setInput('feedback', { referenceId: 999 } as any);
        fixture.componentRef.setInput('assessmentsNames', { 42: { type: 'Model', name: 'Class Diagram' } } as any);
        fixture.detectChanges();
        expect(component.inferredTitle()).toBe('artemisApp.feedback.type.feedback');
    });

    it('should fall back to default title for modeling feedback with text but no detailText, even when assessmentsNames maps the referenceId', () => {
        fixture.componentRef.setInput('title', undefined);
        fixture.componentRef.setInput('points', 0);
        fixture.componentRef.setInput('feedback', { text: 'Instructor comment', referenceId: 42 } as any);
        fixture.componentRef.setInput('assessmentsNames', { 42: { type: 'Model', name: 'Class Diagram' } } as any);
        fixture.detectChanges();
        expect(component.inferredTitle()).toBe('artemisApp.feedback.type.feedback');
    });

    it('should fall back to default title for modeling feedback with text but no detailText, even when referenceId not in assessmentsNames mapping', () => {
        fixture.componentRef.setInput('title', undefined);
        fixture.componentRef.setInput('points', 0);
        fixture.componentRef.setInput('feedback', { text: 'Instructor comment', referenceId: 999 } as any);
        fixture.componentRef.setInput('assessmentsNames', { 42: { type: 'Model', name: 'Class Diagram' } } as any);
        fixture.detectChanges();
        expect(component.inferredTitle()).toBe('artemisApp.feedback.type.feedback');
    });

    it('should return undefined inferredReference when no mapping and no feedback.reference', () => {
        fixture.componentRef.setInput('reference', undefined);
        fixture.componentRef.setInput('showReference', true);
        fixture.componentRef.setInput('feedback', { referenceId: 5 } as any);
        fixture.componentRef.setInput('assessmentsNames', { 42: { type: 'Model', name: 'Class Diagram' } } as any);
        fixture.detectChanges();
        expect(component.inferredReference()).toBeUndefined();
        expect(fixture.nativeElement.querySelector('.unified-feedback-reference-text')).toBeNull();
    });

    it('should expose alert-primary for needs_revision', () => {
        fixture.componentRef.setInput('type', 'needs_revision');
        fixture.detectChanges();
        expect(component.inferredType()).toBe('needs_revision');
        expect(component.inferredAlertClass()).toBe('alert-primary');
        const root = fixture.nativeElement.querySelector('.unified-feedback');
        expect(root.classList.contains('alert-primary')).toBeTruthy();
    });

    it('should expose alert-secondary for not_attempted', () => {
        fixture.componentRef.setInput('type', 'not_attempted');
        fixture.detectChanges();
        expect(component.inferredType()).toBe('not_attempted');
        expect(component.inferredAlertClass()).toBe('alert-secondary');
    });

    it('should expose the faMinus icon for not_attempted', () => {
        fixture.componentRef.setInput('type', 'not_attempted');
        fixture.detectChanges();
        expect(component.inferredIcon()).toBe(faMinus);
    });

    it('should expose alert-danger for non_compliant', () => {
        fixture.componentRef.setInput('type', 'non_compliant');
        fixture.detectChanges();
        expect(component.inferredType()).toBe('non_compliant');
        expect(component.inferredAlertClass()).toBe('alert-danger');
    });

    it('should infer type from feedbackCredits when editable, ignoring the points input', () => {
        fixture.componentRef.setInput('editable', true);
        fixture.componentRef.setInput('points', 5); // must be ignored in editable mode
        component.feedbackCredits.set(-2);
        fixture.detectChanges();
        expect(component.inferredType()).toBe('non_compliant');
        expect(component.inferredAlertClass()).toBe('alert-danger');

        component.feedbackCredits.set(3);
        fixture.detectChanges();
        expect(component.inferredType()).toBe('correct');
        expect(component.inferredAlertClass()).toBe('alert-success');
    });

    it('should expose a stripped display title and mark an accepted suggestion as adapted on title edit', () => {
        fixture.componentRef.setInput('editable', true);
        component.feedbackTitle.set('FeedbackSuggestion:accepted:Missing null check');
        fixture.detectChanges();
        expect(component.displayTitle()).toBe('Missing null check');

        component.onTitleInput('Null check is missing');
        expect(component.feedbackTitle()).toBe('FeedbackSuggestion:adapted:Null check is missing');
    });

    it('should mark an accepted suggestion as adapted when only the description is edited', () => {
        fixture.componentRef.setInput('editable', true);
        component.feedbackTitle.set(`${FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER}Missing null check`);
        fixture.detectChanges();

        component.onDetailChange('More context about the issue');

        expect(component.feedbackDetail()).toBe('More context about the issue');
        expect(component.feedbackTitle()).toBe(`${FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER}Missing null check`);
    });

    it('should mark an accepted suggestion as adapted when only the score is edited', () => {
        fixture.componentRef.setInput('editable', true);
        component.feedbackTitle.set(`${FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER}Missing null check`);
        fixture.detectChanges();

        component.onCreditsChange(2);

        expect(component.feedbackCredits()).toBe(2);
        expect(component.feedbackTitle()).toBe(`${FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER}Missing null check`);
    });

    it('should not touch a non-suggestion title when editing', () => {
        fixture.componentRef.setInput('editable', true);
        component.feedbackTitle.set('Encapsulation broken');
        fixture.detectChanges();

        component.onCreditsChange(2);
        component.onDetailChange('Some detail');
        component.onTitleInput('Encapsulation is broken');

        expect(component.feedbackTitle()).toBe('Encapsulation is broken');
    });

    it('should step the credits up and down by half a point', () => {
        fixture.componentRef.setInput('editable', true);
        component.feedbackCredits.set(1);
        fixture.detectChanges();

        component.stepCredits(CREDITS_STEP);
        expect(component.feedbackCredits()).toBe(1.5);

        component.stepCredits(-CREDITS_STEP);
        expect(component.feedbackCredits()).toBe(1);
    });

    it('should snap a hand-typed value onto the half-point grid in the direction of travel when stepping', () => {
        fixture.componentRef.setInput('editable', true);
        component.feedbackCredits.set(1.3);
        fixture.detectChanges();

        component.stepCredits(CREDITS_STEP);
        expect(component.feedbackCredits()).toBe(1.5);

        component.feedbackCredits.set(1.3);
        component.stepCredits(-CREDITS_STEP);
        expect(component.feedbackCredits()).toBe(1);
    });

    it('should normalize hand-typed credits onto the half-point grid', () => {
        fixture.componentRef.setInput('editable', true);

        component.onCreditsChange(0.3);
        expect(component.feedbackCredits()).toBe(0.5);

        component.onCreditsChange(-0.3);
        expect(component.feedbackCredits()).toBe(-0.5);
    });

    it('should not step the credits when read-only or linked to a grading instruction', () => {
        fixture.componentRef.setInput('editable', true);
        fixture.componentRef.setInput('readOnly', true);
        component.feedbackCredits.set(1);
        fixture.detectChanges();

        component.stepCredits(CREDITS_STEP);
        expect(component.feedbackCredits()).toBe(1);

        fixture.componentRef.setInput('readOnly', false);
        fixture.componentRef.setInput('feedback', { credits: 1, gradingInstruction: { feedback: 'Fixed rubric text', credits: 1 } } as any);
        fixture.detectChanges();

        component.stepCredits(CREDITS_STEP);
        expect(component.feedbackCredits()).toBe(1);
    });

    it('should stay adapted once already adapted, even if a later edit happens to match the original wording', () => {
        fixture.componentRef.setInput('editable', true);
        component.feedbackTitle.set(`${FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER}Missing null check`);
        fixture.detectChanges();

        component.onTitleInput('Missing null check');

        expect(component.feedbackTitle()).toBe(`${FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER}Missing null check`);
    });

    it('should feed the live feedbackTitle into the suggestion badge, reflecting the adapted state within the same session (regression test for the pre-fix object-identity staleness bug)', () => {
        fixture.componentRef.setInput('editable', true);
        fixture.componentRef.setInput('feedback', { text: `${FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER}Missing null check` } as any);
        component.feedbackTitle.set(`${FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER}Missing null check`);
        fixture.detectChanges();

        let badge = fixture.debugElement.query(By.directive(FeedbackSuggestionBadgeComponent));
        expect(badge.componentInstance.feedbackText()).toBe(`${FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER}Missing null check`);

        component.onCreditsChange(3);
        fixture.detectChanges();

        badge = fixture.debugElement.query(By.directive(FeedbackSuggestionBadgeComponent));
        expect(badge.componentInstance.feedbackText()).toBe(`${FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER}Missing null check`);
    });

    it('should not add a prefix when editing a plain (non-suggestion) title', () => {
        fixture.componentRef.setInput('editable', true);
        component.feedbackTitle.set(undefined);
        fixture.detectChanges();
        expect(component.displayTitle()).toBe('');

        component.onTitleInput('Encapsulation broken');
        expect(component.feedbackTitle()).toBe('Encapsulation broken');
    });

    it('should expose the auto-derived label as a placeholder for the title input', () => {
        fixture.componentRef.setInput('editable', true);
        component.feedbackCredits.set(2);
        fixture.detectChanges();
        expect(component.defaultTitlePlaceholder()).toBe('artemisApp.feedback.type.positive');
    });

    it('should allow dismissal without confirmation only when credits are 0, detail text is empty, and title is empty', () => {
        fixture.componentRef.setInput('editable', true);
        component.feedbackCredits.set(0);
        component.feedbackDetail.set('');
        fixture.detectChanges();
        expect(component.canDismissWithoutConfirm()).toBe(true);

        component.feedbackDetail.set('Some text');
        fixture.detectChanges();
        expect(component.canDismissWithoutConfirm()).toBe(false);

        component.feedbackDetail.set('');
        component.feedbackCredits.set(1);
        fixture.detectChanges();
        expect(component.canDismissWithoutConfirm()).toBe(false);

        component.feedbackCredits.set(0);
        component.feedbackTitle.set('Encapsulation broken');
        fixture.detectChanges();
        expect(component.canDismissWithoutConfirm()).toBe(false);
    });

    it('should treat undefined feedbackCredits as 0 when checking dismissal without confirmation', () => {
        fixture.componentRef.setInput('editable', true);
        component.feedbackCredits.set(undefined as unknown as number);
        component.feedbackDetail.set('');
        fixture.detectChanges();
        expect(component.canDismissWithoutConfirm()).toBe(true);
    });

    it('should require confirmation to dismiss a zero-credit grading-instruction feedback even without free-form text', () => {
        fixture.componentRef.setInput('editable', true);
        fixture.componentRef.setInput('feedback', { gradingInstruction: { id: 1, credits: 0, feedback: 'Rubric text' } } as Feedback);
        component.feedbackCredits.set(0);
        component.feedbackDetail.set('');
        component.feedbackTitle.set('');
        fixture.detectChanges();
        expect(component.canDismissWithoutConfirm()).toBe(false);
    });

    it('should require confirmation to dismiss a persisted zero-credit feedback even without free-form text', () => {
        fixture.componentRef.setInput('editable', true);
        fixture.componentRef.setInput('feedback', { id: 1 } as Feedback);
        component.feedbackCredits.set(0);
        component.feedbackDetail.set('');
        component.feedbackTitle.set('');
        fixture.detectChanges();
        expect(component.canDismissWithoutConfirm()).toBe(false);
    });

    it('should emit onDelete directly when dismissal needs no confirmation', () => {
        fixture.componentRef.setInput('editable', true);
        component.feedbackCredits.set(0);
        component.feedbackDetail.set('');
        fixture.detectChanges();
        const emitSpy = vi.fn();
        component.onDelete.subscribe(emitSpy);

        component.toggleDeleteConfirm();

        expect(emitSpy).toHaveBeenCalledOnce();
    });

    it('should emit onDelete when handleDeleteConfirmed is called', () => {
        const emitSpy = vi.fn();
        component.onDelete.subscribe(emitSpy);

        component.handleDeleteConfirmed();

        expect(emitSpy).toHaveBeenCalledOnce();
    });

    it('should render an editable header with title input, points input, and delete toolbar', async () => {
        fixture.componentRef.setInput('editable', true);
        component.feedbackCredits.set(2);
        component.feedbackDetail.set('Some detail');
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const titleInput = fixture.nativeElement.querySelector('.unified-feedback-title-input') as HTMLTextAreaElement;
        const pointsInput = fixture.nativeElement.querySelector('.unified-feedback-points-input') as HTMLInputElement;
        const detailInput = fixture.nativeElement.querySelector('.unified-feedback-detail-input') as HTMLTextAreaElement;

        expect(titleInput).toBeTruthy();
        expect(pointsInput).toBeTruthy();
        expect(detailInput).toBeTruthy();
        expect(detailInput.value).toBe('Some detail');
        // Points are only ever changed via the +/- steppers, never by typing.
        expect(pointsInput.readOnly).toBe(true);
        // credits=2, detail non-empty => confirmation required, so the plain dismiss button must not render
        expect(fixture.nativeElement.querySelector('#dismiss-icon')).toBeNull();
        expect(fixture.nativeElement.querySelector('#confirm-icon')).toBeTruthy();
    });

    it('should not change the points when typing into the (readonly) points input', async () => {
        fixture.componentRef.setInput('editable', true);
        component.feedbackCredits.set(1);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const pointsInput = fixture.nativeElement.querySelector('.unified-feedback-points-input') as HTMLInputElement;
        pointsInput.value = '99';
        pointsInput.dispatchEvent(new Event('input'));
        fixture.detectChanges();

        expect(component.feedbackCredits()).toBe(1);
    });

    it('should increment and decrement the points via the stepper buttons', async () => {
        fixture.componentRef.setInput('editable', true);
        component.feedbackCredits.set(1);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const steps = fixture.nativeElement.querySelectorAll('.unified-feedback-points-step') as NodeListOf<HTMLButtonElement>;
        expect(steps).toHaveLength(2);

        steps[1].click();
        expect(component.feedbackCredits()).toBe(1.5);

        steps[0].click();
        steps[0].click();
        expect(component.feedbackCredits()).toBe(0.5);
    });

    it('should render the plain dismiss button when nothing would be lost', () => {
        fixture.componentRef.setInput('editable', true);
        component.feedbackCredits.set(0);
        component.feedbackDetail.set('');
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('#dismiss-icon')).toBeTruthy();
        expect(fixture.nativeElement.querySelector('#confirm-icon')).toBeNull();
    });

    it('should emit onDelete when the plain dismiss button is clicked', () => {
        fixture.componentRef.setInput('editable', true);
        component.feedbackCredits.set(0);
        component.feedbackDetail.set('');
        fixture.detectChanges();
        const emitSpy = vi.fn();
        component.onDelete.subscribe(emitSpy);

        (fixture.nativeElement.querySelector('#dismiss-icon') as HTMLButtonElement).click();

        expect(emitSpy).toHaveBeenCalledOnce();
    });

    it('should show the title input by default when editable, keeping existing consumers unchanged', async () => {
        fixture.componentRef.setInput('editable', true);
        component.feedbackTitle.set('Encapsulation broken');
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.titleEditable()).toBe(true);
        expect(fixture.nativeElement.querySelector('.unified-feedback-title-input')).toBeTruthy();
        expect(fixture.nativeElement.querySelector('.unified-feedback-title')).toBeNull();
    });

    it('should render the read-only title instead of the title input when titleEditable is false', async () => {
        fixture.componentRef.setInput('editable', true);
        fixture.componentRef.setInput('titleEditable', false);
        fixture.componentRef.setInput('title', 'File Sort.java at line 4');
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('.unified-feedback-title-input')).toBeNull();
        const title = fixture.nativeElement.querySelector('.unified-feedback-title');
        expect(title).toBeTruthy();
        expect(title.textContent).toContain('File Sort.java at line 4');
    });

    it('should show the grading instruction label and lock the points input when a grading instruction is attached', async () => {
        fixture.componentRef.setInput('editable', true);
        fixture.componentRef.setInput('feedback', { credits: 2, gradingInstruction: { feedback: 'Fixed rubric text', credits: 2 } } as any);
        component.feedbackCredits.set(2);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const label = fixture.nativeElement.querySelector('.unified-feedback-rubric-label');
        const pointsInput = fixture.nativeElement.querySelector('.unified-feedback-points-input') as HTMLInputElement;
        const steps = fixture.nativeElement.querySelectorAll('.unified-feedback-points-step') as NodeListOf<HTMLButtonElement>;

        expect(label?.textContent).toContain('Fixed rubric text');
        expect(pointsInput.disabled).toBe(true);
        expect(steps[0].disabled).toBe(true);
        expect(steps[1].disabled).toBe(true);
    });

    it('should disable the steppers and show the rubric label after a grading instruction is assigned in place post-render (regression test for the stale-computed rubric-state bug)', () => {
        fixture.componentRef.setInput('editable', true);
        const feedback = { credits: 0 } as Feedback;
        fixture.componentRef.setInput('feedback', feedback);
        component.feedbackCredits.set(0);
        fixture.detectChanges();

        let steps = fixture.nativeElement.querySelectorAll('.unified-feedback-points-step') as NodeListOf<HTMLButtonElement>;
        expect(steps[0].disabled).toBe(false);
        expect(steps[1].disabled).toBe(false);
        expect(fixture.nativeElement.querySelector('.unified-feedback-rubric-label')).toBeNull();

        // Mirrors StructuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent and
        // UnreferencedFeedbackDetailComponent.updateFeedbackOnDrop: the grading instruction is assigned onto the
        // existing feedback object in place, and the model is re-set with that same object reference.
        feedback.gradingInstruction = { feedback: 'Fixed rubric text', credits: 2 } as any;
        fixture.componentRef.setInput('feedback', feedback);
        component.feedbackCredits.set(2);
        fixture.detectChanges();

        steps = fixture.nativeElement.querySelectorAll('.unified-feedback-points-step') as NodeListOf<HTMLButtonElement>;
        expect(steps[0].disabled).toBe(true);
        expect(steps[1].disabled).toBe(true);
        expect(fixture.nativeElement.querySelector('.unified-feedback-rubric-label')?.textContent).toContain('Fixed rubric text');
    });

    it('should not render a footer when the feedback is not a suggestion', () => {
        fixture.componentRef.setInput('feedback', undefined);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('.unified-feedback-footer')).toBeNull();
    });

    it('should render the AI suggestion badge inside a footer when feedback.text carries a suggestion prefix and editable is true', () => {
        fixture.componentRef.setInput('editable', true);
        fixture.componentRef.setInput('feedback', { text: `${FEEDBACK_SUGGESTION_IDENTIFIER}Missing null check` } as any);
        fixture.detectChanges();

        const footer = fixture.nativeElement.querySelector('.unified-feedback-footer');
        expect(footer).toBeTruthy();
        expect(footer.querySelector('jhi-feedback-suggestion-badge')).toBeTruthy();
    });

    it('should not render the AI suggestion badge in non-editable (e.g. student result) views, even when the feedback is a suggestion', () => {
        fixture.componentRef.setInput('editable', false);
        fixture.componentRef.setInput('feedback', { text: `${FEEDBACK_SUGGESTION_IDENTIFIER}Missing null check` } as any);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('.unified-feedback-footer')).toBeNull();
    });

    it('should reflect correctionStatus mutated in place after initial render, both showing and clearing the label', () => {
        const feedback = { correctionStatus: 'CORRECT' } as any;
        fixture.componentRef.setInput('feedback', feedback);
        fixture.detectChanges();

        expect(component.correctionStatusLabel()).toBeDefined();
        expect(component.isCorrectionStatusCorrect()).toBe(true);

        feedback.correctionStatus = 'INCORRECT';
        fixture.detectChanges();

        expect(component.isCorrectionStatusCorrect()).toBe(false);

        delete feedback.correctionStatus;
        fixture.detectChanges();

        expect(component.correctionStatusLabel()).toBeUndefined();
    });
});
