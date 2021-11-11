import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { spy } from 'sinon';

import { CodeEditorTutorAssessmentInlineFeedbackComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-inline-feedback.component';
import { ArtemisProgrammingManualAssessmentModule } from 'app/exercises/programming/assess/programming-manual-assessment.module';
import { FeedbackType } from 'app/entities/feedback.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { MockModule } from 'ng-mocks';
import { ClipboardModule } from 'ngx-clipboard';

chai.use(sinonChai);
const expect = chai.expect;

describe('CodeEditorTutorAssessmentInlineFeedbackComponent', () => {
    let comp: CodeEditorTutorAssessmentInlineFeedbackComponent;
    let fixture: ComponentFixture<CodeEditorTutorAssessmentInlineFeedbackComponent>;
    let sgiService: StructuredGradingCriterionService;
    const fileName = 'testFile';
    const codeLine = 1;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), MockModule(ClipboardModule), ArtemisProgrammingManualAssessmentModule],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                // Ignore console errors
                console.error = () => {
                    return false;
                };
                fixture = TestBed.createComponent(CodeEditorTutorAssessmentInlineFeedbackComponent);
                comp = fixture.componentInstance;
                // @ts-ignore
                comp.feedback = undefined;
                comp.readOnly = false;
                comp.selectedFile = fileName;
                comp.codeLine = codeLine;
                sgiService = fixture.debugElement.injector.get(StructuredGradingCriterionService);
            });
    });

    it('should update feedback and emit to parent', () => {
        const onUpdateFeedbackSpy = spy(comp.onUpdateFeedback, 'emit');
        comp.updateFeedback();

        expect(comp.feedback.reference).to.be.equal(`file:${fileName}_line:${codeLine}`);
        expect(comp.feedback.type).to.be.equal(FeedbackType.MANUAL);
        expect(onUpdateFeedbackSpy).to.be.calledOnceWithExactly(comp.feedback);
    });

    it('should enable edit feedback and emit to parent', () => {
        const onEditFeedbackSpy = spy(comp.onEditFeedback, 'emit');
        comp.editFeedback(codeLine);

        expect(onEditFeedbackSpy).to.be.calledOnceWithExactly(codeLine);
    });

    it('should cancel feedback and emit to parent', () => {
        const onCancelFeedbackSpy = spy(comp.onCancelFeedback, 'emit');
        comp.cancelFeedback();

        expect(onCancelFeedbackSpy).to.be.calledOnceWithExactly(codeLine);
    });

    it('should delete feedback and emit to parent', () => {
        const onDeleteFeedbackSpy = spy(comp.onDeleteFeedback, 'emit');
        global.confirm = () => true;
        const confirmSpy = spy(window, 'confirm');
        comp.deleteFeedback();

        expect(confirmSpy).to.be.calledOnce;
        expect(onDeleteFeedbackSpy).to.be.calledOnceWithExactly(comp.feedback);
    });

    it('should update feedback with SGI and emit to parent', () => {
        const instruction: GradingInstruction = { id: 1, credits: 2, feedback: 'test', gradingScale: 'good', instructionDescription: 'description of instruction', usageCount: 0 };
        // Fake call as a DragEvent cannot be created programmatically
        jest.spyOn(sgiService, 'updateFeedbackWithStructuredGradingInstructionEvent').mockImplementation(() => {
            comp.feedback.gradingInstruction = instruction;
            comp.feedback.credits = instruction.credits;
            comp.feedback.detailText = instruction.feedback;
        });
        // Call spy function with empty event
        comp.updateFeedbackOnDrop(new Event(''));

        expect(comp.feedback.gradingInstruction).to.be.equal(instruction);
        expect(comp.feedback.credits).to.be.equal(instruction.credits);
        expect(comp.feedback.detailText).to.be.equal(instruction.feedback);
        expect(comp.feedback.reference).to.be.equal(`file:${fileName}_line:${codeLine}`);
    });
});
