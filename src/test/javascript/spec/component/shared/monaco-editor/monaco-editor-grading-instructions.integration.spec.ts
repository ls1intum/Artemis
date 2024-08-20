import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { ArtemisTestModule } from '../../../test.module';
import { MonacoEditorModule } from 'app/shared/monaco-editor/monaco-editor.module';
import { MockResizeObserver } from '../../../helpers/mocks/service/mock-resize-observer';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MonacoGradingInstructionAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-instruction.action';
import { MonacoGradingCreditsAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-credits.action';
import { MonacoGradingScaleAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-scale.action';
import { MonacoGradingDescriptionAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-description.action';
import { MonacoGradingFeedbackAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-feedback.action';
import { MonacoGradingUsageCountAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-usage-count.action';
import { MonacoGradingCriterionAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-criterion.action';

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
        const creditsAction = new MonacoGradingCreditsAction();
        const scaleAction = new MonacoGradingScaleAction();
        const descriptionAction = new MonacoGradingDescriptionAction();
        const feedbackAction = new MonacoGradingFeedbackAction();
        const usageCountAction = new MonacoGradingUsageCountAction();
        const instructionAction = new MonacoGradingInstructionAction(creditsAction, scaleAction, descriptionAction, feedbackAction, usageCountAction);
        const criterionAction = new MonacoGradingCriterionAction(instructionAction);
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
