import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TextblockFeedbackDropdownComponent } from 'app/exercises/text/assess/textblock-feedback-editor/dropdown/textblock-feedback-dropdown.component';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { MockComponent } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';

describe('TextblockFeedbackDropdownComponent', () => {
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

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [TextblockFeedbackDropdownComponent, MockComponent(HelpIconComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TextblockFeedbackDropdownComponent);
                component = fixture.componentInstance;
                component.criterion = criterion;
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should update assessment correctly when using dropdown-menu', () => {
        const changeSpy = jest.spyOn(component.didChange, 'emit');
        component.updateAssessmentWithDropdown(gradingInstruction);

        expect(changeSpy).toHaveBeenCalledOnce();
        expect(component.feedback.gradingInstruction).toEqual(gradingInstruction);
    });

    it('should display correct background colors for dropdown elements', () => {
        expect(component.getInstrColour(gradingInstruction)).toBe('var(--sgi-assessment-layout-positive-background)');
        gradingInstruction.credits = 0;
        fixture.detectChanges();
        expect(component.getInstrColour(gradingInstruction)).toBe('var(--sgi-assessment-layout-zero-background)');
        gradingInstruction.credits = -1;
        fixture.detectChanges();
        expect(component.getInstrColour(gradingInstruction)).toBe('var(--sgi-assessment-layout-negative-background)');
    });
});
