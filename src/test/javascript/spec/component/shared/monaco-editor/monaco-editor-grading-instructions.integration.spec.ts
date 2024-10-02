import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { ArtemisTestModule } from '../../../test.module';
import { MonacoEditorModule } from 'app/shared/monaco-editor/monaco-editor.module';
import { MockResizeObserver } from '../../../helpers/mocks/service/mock-resize-observer';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GradingInstructionAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-instruction.action';
import { GradingCreditsAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-credits.action';
import { GradingScaleAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-scale.action';
import { GradingDescriptionAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-description.action';
import { GradingFeedbackAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-feedback.action';
import { GradingUsageCountAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-usage-count.action';
import { GradingCriterionAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/grading-criterion.action';

describe('MonacoEditorActionGradingInstructionsIntegration', () => {
    let fixture: ComponentFixture<MonacoEditorComponent>;
    let comp: MonacoEditorComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MonacoEditorModule],
            declarations: [MonacoEditorComponent],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MonacoEditorComponent);
                comp = fixture.componentInstance;
                global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
                    return new MockResizeObserver(callback);
                });
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    const setupActions = () => {
        const creditsAction = new GradingCreditsAction();
        const scaleAction = new GradingScaleAction();
        const descriptionAction = new GradingDescriptionAction();
        const feedbackAction = new GradingFeedbackAction();
        const usageCountAction = new GradingUsageCountAction();
        const instructionAction = new GradingInstructionAction(creditsAction, scaleAction, descriptionAction, feedbackAction, usageCountAction);
        const criterionAction = new GradingCriterionAction(instructionAction);
        [creditsAction, scaleAction, descriptionAction, feedbackAction, usageCountAction, instructionAction, criterionAction].forEach((action) => comp.registerAction(action));
        return { creditsAction, scaleAction, descriptionAction, feedbackAction, usageCountAction, instructionAction, criterionAction };
    };

    const expectedInstructionTextWithoutCriterion =
        '\n[instruction]' +
        '\n\t[credits] 0' +
        '\n\t[gradingScale] Add instruction grading scale here (only visible for tutors)' +
        '\n\t[description] Add grading instruction here (only visible for tutors)' +
        '\n\t[feedback] Add feedback for students here (visible for students)' +
        '\n\t[maxCountInScore] 0';

    const generalInstructionText = 'These are some general instructions for the tutors.';

    it('should insert grading instructions', () => {
        comp.triggerKeySequence(generalInstructionText);
        const actions = setupActions();
        actions.instructionAction.executeInCurrentEditor();
        expect(comp.getText()).toBe(generalInstructionText + expectedInstructionTextWithoutCriterion);
    });

    it('should insert grading criterion', () => {
        comp.triggerKeySequence(generalInstructionText);
        const actions = setupActions();
        actions.criterionAction.executeInCurrentEditor();
        expect(comp.getText()).toBe(`${generalInstructionText}\n[criterion] Add criterion title (only visible to tutors)${expectedInstructionTextWithoutCriterion}`);
    });
});
