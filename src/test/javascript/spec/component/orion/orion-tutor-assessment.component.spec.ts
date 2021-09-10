import { TestBed } from '@angular/core/testing';
import * as chai from 'chai';
import * as sinon from 'sinon';
import sinonChai from 'sinon-chai';
import { spy } from 'sinon';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { OrionAssessmentService } from 'app/orion/assessment/orion-assessment.service';
import { OrionTutorAssessmentComponent } from 'app/orion/assessment/orion-tutor-assessment.component';
import { CodeEditorTutorAssessmentContainerComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-container.component';
import { OrionAssessmentInstructionsComponent } from 'app/orion/assessment/orion-assessment-instructions.component';
import { AlertService } from 'app/core/util/alert.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('OrionTutorAssessmentComponent', () => {
    let comp: OrionTutorAssessmentComponent;
    let orionConnectorService: OrionConnectorService;
    let container: CodeEditorTutorAssessmentContainerComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                OrionTutorAssessmentComponent,
                MockComponent(CodeEditorTutorAssessmentContainerComponent),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(OrionAssessmentInstructionsComponent),
            ],
            providers: [
                MockProvider(OrionConnectorService),
                MockProvider(OrionAssessmentService),
                MockProvider(AlertService),
                MockProvider(TranslateService),
                MockProvider(CodeEditorTutorAssessmentContainerComponent),
            ],
        })
            .compileComponents()
            .then(() => {
                comp = TestBed.createComponent(OrionTutorAssessmentComponent).componentInstance;
                orionConnectorService = TestBed.inject(OrionConnectorService);
                container = TestBed.inject(CodeEditorTutorAssessmentContainerComponent);
                comp.container = container;
                container.submission = { id: 5 };
                container.referencedFeedback = [{ id: 5 }, { id: 6 }];
                container.exerciseId = 15;
                container.correctionRound = 1;
            });
    });
    afterEach(() => {
        sinon.restore();
    });

    it('initializeFeedback should call connector', () => {
        const initializeAssessmentSpy = spy(orionConnectorService, 'initializeAssessment');

        comp.initializeFeedback();

        expect(initializeAssessmentSpy).to.have.been.calledOnceWithExactly(5, [{ id: 5 }, { id: 6 }]);
    });
    it('updateFeedback should call connector', () => {
        const updateFeedbackSpy = spy(container, 'onUpdateFeedback');

        comp.updateFeedback(5, [{ id: 1 }]);

        expect(updateFeedbackSpy).to.have.been.calledOnceWithExactly([{ id: 1 }]);
    });
    it('updateFeedback should throw error', () => {
        const errorSpy = spy(TestBed.inject(AlertService), 'error');

        comp.updateFeedback(10, [{ id: 1 }]);

        expect(errorSpy).to.have.been.calledOnceWithExactly('artemisApp.orion.assessment.submissionIdDontMatch');
    });
    it('openNextSubmission should call service', () => {
        const sendSubmissionToOrionCancellableSpy = spy(TestBed.inject(OrionAssessmentService), 'sendSubmissionToOrionCancellable');

        comp.openNextSubmission(2);

        expect(sendSubmissionToOrionCancellableSpy).to.have.been.calledOnceWithExactly(15, 2, 1);
    });
});
