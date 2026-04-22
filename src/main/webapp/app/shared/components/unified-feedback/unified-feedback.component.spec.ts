import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UnifiedFeedbackComponent } from './unified-feedback.component';
import { TranslateModule } from '@ngx-translate/core';

describe('UnifiedFeedbackComponent', () => {
    setupTestBed({ zoneless: true });
    let component: UnifiedFeedbackComponent;
    let fixture: ComponentFixture<UnifiedFeedbackComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UnifiedFeedbackComponent, TranslateModule.forRoot()],
        }).compileComponents();

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

    it('should infer needs_revision type by default when points = 0', () => {
        expect(component.inferredType()).toBe('needs_revision');
        expect(component.inferredTitle()).toBe('artemisApp.feedback.type.needsRevision');
        expect(component.inferredAlertClass()).toBe('alert-secondary');
    });

    it('should return correct alert class for default needs_revision type', () => {
        expect(component.inferredAlertClass()).toBe('alert-secondary');
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
        expect(component.inferredTitle()).toBe('artemisApp.feedback.type.needsRevision');
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
        expect(component.inferredTitle()).toBe('artemisApp.feedback.type.needsRevision');
        fixture.componentRef.setInput('points', 2);
        fixture.detectChanges();
        expect(component.inferredTitle()).toBe('artemisApp.feedback.type.correct');
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
        expect(component.inferredReference()).toBe('ER Diagram');
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

    it('should fall back to default title when mapping is present but id missing', () => {
        fixture.componentRef.setInput('title', undefined);
        fixture.componentRef.setInput('points', 0);
        fixture.componentRef.setInput('feedback', { referenceId: 999 } as any);
        fixture.componentRef.setInput('assessmentsNames', { 42: { type: 'Model', name: 'Class Diagram' } } as any);
        fixture.detectChanges();
        expect(component.inferredTitle()).toBe('artemisApp.feedback.type.needsRevision');
    });

    it('should use default title and not assessmentsNames when feedback.text is set without detailText', () => {
        fixture.componentRef.setInput('title', undefined);
        fixture.componentRef.setInput('points', 0);
        fixture.componentRef.setInput('feedback', { text: 'Override description', referenceId: 42 } as any);
        fixture.componentRef.setInput('assessmentsNames', { 42: { type: 'Model', name: 'Class Diagram' } } as any);
        fixture.detectChanges();
        expect(component.inferredTitle()).toBe('artemisApp.feedback.type.needsRevision');
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

    it('should expose alert-secondary for needs_revision', () => {
        fixture.componentRef.setInput('type', 'needs_revision');
        fixture.detectChanges();
        expect(component.inferredType()).toBe('needs_revision');
        expect(component.inferredAlertClass()).toBe('alert-secondary');
        const root = fixture.nativeElement.querySelector('.unified-feedback');
        expect(root.classList.contains('alert-secondary')).toBeTruthy();
    });

    it('should expose alert-secondary for not_attempted', () => {
        fixture.componentRef.setInput('type', 'not_attempted');
        fixture.detectChanges();
        expect(component.inferredType()).toBe('not_attempted');
        expect(component.inferredAlertClass()).toBe('alert-secondary');
    });

    it('should expose alert-danger for non_compliant', () => {
        fixture.componentRef.setInput('type', 'non_compliant');
        fixture.detectChanges();
        expect(component.inferredType()).toBe('non_compliant');
        expect(component.inferredAlertClass()).toBe('alert-danger');
    });
});
