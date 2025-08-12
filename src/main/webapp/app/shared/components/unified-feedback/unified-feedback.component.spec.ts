import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FeedbackIconType, UnifiedFeedbackComponent } from './unified-feedback.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faExclamationTriangle, faRedo } from '@fortawesome/free-solid-svg-icons';

describe('UnifiedFeedbackComponent', () => {
    let component: UnifiedFeedbackComponent;
    let fixture: ComponentFixture<UnifiedFeedbackComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UnifiedFeedbackComponent, FaIconComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(UnifiedFeedbackComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should return correct icon for success type', () => {
        component.icon = 'success';
        expect(component.getIconForType()).toBe(faCheck);
    });

    it('should return correct icon for error type', () => {
        component.icon = 'error';
        expect(component.getIconForType()).toBe(faExclamationTriangle);
    });

    it('should return correct icon for retry type', () => {
        component.icon = 'retry';
        expect(component.getIconForType()).toBe(faRedo);
    });

    it('should return correct alert class for success type', () => {
        component.icon = 'success';
        expect(component.getAlertClass()).toBe('alert-success');
    });

    it('should return correct alert class for error type', () => {
        component.icon = 'error';
        expect(component.getAlertClass()).toBe('alert-danger');
    });

    it('should return correct alert class for retry type', () => {
        component.icon = 'retry';
        expect(component.getAlertClass()).toBe('alert-warning');
    });
});
