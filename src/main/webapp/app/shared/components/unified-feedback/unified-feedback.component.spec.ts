import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UnifiedFeedbackComponent } from './unified-feedback.component';

describe('UnifiedFeedbackComponent', () => {
    let component: UnifiedFeedbackComponent;
    let fixture: ComponentFixture<UnifiedFeedbackComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UnifiedFeedbackComponent],
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

    it('should infer not_attempted type by default when points = 0', () => {
        expect(component.inferredType()).toBe('not_attempted');
        expect(component.inferredTitle()).toBe('Not Attempted');
        expect(component.inferredAlertClass()).toBe('alert-danger');
    });

    it('should return correct icons for each type', () => {
        expect(component.inferredIcon()).toBe(component.faTimes); // default for not_attempted
    });

    it('should return correct alert classes for each type', () => {
        expect(component.inferredAlertClass()).toBe('alert-danger'); // default for not_attempted
    });

    it('should have legacy icon support', () => {
        expect(component.icon()).toBe('success');
        expect(component.getIconForType()).toBe(component.inferredIcon());
        expect(component.getAlertClass()).toBe(component.inferredAlertClass());
    });
});
