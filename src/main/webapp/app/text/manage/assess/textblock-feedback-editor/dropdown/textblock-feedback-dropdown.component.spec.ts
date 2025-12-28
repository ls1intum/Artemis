/**
 * Tests for TextblockFeedbackDropdownComponent.
 * This test suite verifies the feedback dropdown functionality including:
 * - Component initialization
 * - Assessment updates via dropdown menu
 * - Background color display based on grading instruction credits
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TextblockFeedbackDropdownComponent } from 'app/text/manage/assess/textblock-feedback-editor/dropdown/textblock-feedback-dropdown.component';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { MockComponent } from 'ng-mocks';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';

describe('TextblockFeedbackDropdownComponent', () => {
    setupTestBed({ zoneless: true });
    let component: TextblockFeedbackDropdownComponent;
    let fixture: ComponentFixture<TextblockFeedbackDropdownComponent>;

    const gradingInstruction = {
        credits: 1,
        gradingScale: 'Good',
        instructionDescription: 'Apply if it is well done',
        feedback: 'Well done!',
    } as GradingInstruction;

    const criterion = {
        title: 'Assessment of Name',
        structuredGradingInstructions: [gradingInstruction],
    } as GradingCriterion;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TextblockFeedbackDropdownComponent],
        })
            .overrideComponent(TextblockFeedbackDropdownComponent, {
                set: {
                    imports: [MockComponent(HelpIconComponent)],
                },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TextblockFeedbackDropdownComponent);
                component = fixture.componentInstance;
                // Use setInput for signal inputs
                fixture.componentRef.setInput('criterion', criterion);
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should update assessment correctly when using dropdown-menu', () => {
        const changeSpy = vi.spyOn(component.didChange, 'emit');
        component.updateAssessmentWithDropdown(gradingInstruction);

        expect(changeSpy).toHaveBeenCalledOnce();
        expect(component.feedback().gradingInstruction).toEqual(gradingInstruction);
    });

    it('should display correct background colors for dropdown elements', () => {
        expect(component.getInstrColour(gradingInstruction)).toBe('var(--sgi-assessment-layout-positive-background)');
        gradingInstruction.credits = 0;
        fixture.changeDetectorRef.detectChanges();
        expect(component.getInstrColour(gradingInstruction)).toBe('var(--sgi-assessment-layout-zero-background)');
        gradingInstruction.credits = -1;
        fixture.changeDetectorRef.detectChanges();
        expect(component.getInstrColour(gradingInstruction)).toBe('var(--sgi-assessment-layout-negative-background)');
    });
});
