import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { spy } from 'sinon';

import { CodeEditorTutorAssessmentInlineFeedbackComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-inline-feedback.component';
import { ArtemisProgrammingManualAssessmentModule } from 'app/exercises/programming/assess/programming-manual-assessment.module';
import { FeedbackType } from 'app/entities/feedback.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('CodeEditorTutorAssessmentInlineFeedbackComponent', () => {
    let comp: CodeEditorTutorAssessmentInlineFeedbackComponent;
    let fixture: ComponentFixture<CodeEditorTutorAssessmentInlineFeedbackComponent>;
    let debugElement: DebugElement;
    const fileName = 'testFile';
    const codeLine = 1;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisProgrammingManualAssessmentModule],
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
                debugElement = fixture.debugElement;
                // @ts-ignore
                comp.feedback = undefined;
                comp.readOnly = false;
                comp.selectedFile = fileName;
                comp.codeLine = codeLine;
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
});
