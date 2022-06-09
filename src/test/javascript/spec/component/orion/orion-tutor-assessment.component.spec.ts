import { TestBed } from '@angular/core/testing';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { OrionAssessmentService } from 'app/orion/assessment/orion-assessment.service';
import { OrionTutorAssessmentComponent } from 'app/orion/assessment/orion-tutor-assessment.component';
import { CodeEditorTutorAssessmentContainerComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-container.component';
import { OrionAssessmentInstructionsComponent } from 'app/orion/assessment/orion-assessment-instructions.component';
import { AlertService } from 'app/core/util/alert.service';

describe('OrionTutorAssessmentComponent', () => {
    let comp: OrionTutorAssessmentComponent;
    let orionConnectorService: OrionConnectorService;
    let orionAssessmentService: OrionAssessmentService;
    let alertService: AlertService;
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
            providers: [MockProvider(OrionConnectorService), MockProvider(OrionAssessmentService), MockProvider(CodeEditorTutorAssessmentContainerComponent)],
        })
            .compileComponents()
            .then(() => {
                comp = TestBed.createComponent(OrionTutorAssessmentComponent).componentInstance;
                orionConnectorService = TestBed.inject(OrionConnectorService);
                orionAssessmentService = TestBed.inject(OrionAssessmentService);
                alertService = TestBed.inject(AlertService);
                container = TestBed.inject(CodeEditorTutorAssessmentContainerComponent);
                comp.container = container;
                container.submission = { id: 5 };
                container.referencedFeedback = [{ id: 5 }, { id: 6 }];
                container.exerciseId = 15;
                container.correctionRound = 1;
                container.isTestRun = false;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('initializeFeedback should call connector', () => {
        const initializeAssessmentSpy = jest.spyOn(orionConnectorService, 'initializeAssessment');

        comp.initializeFeedback();

        expect(initializeAssessmentSpy).toHaveBeenCalledOnce();
        expect(initializeAssessmentSpy).toHaveBeenCalledWith(5, [{ id: 5 }, { id: 6 }]);
    });

    it('updateFeedback should call connector', () => {
        const updateFeedbackSpy = jest.spyOn(container, 'onUpdateFeedback');

        comp.updateFeedback(5, [{ id: 1 }]);

        expect(updateFeedbackSpy).toHaveBeenCalledOnce();
        expect(updateFeedbackSpy).toHaveBeenCalledWith([{ id: 1 }]);
    });

    it('updateFeedback should throw error', () => {
        const errorSpy = jest.spyOn(alertService, 'error');

        comp.updateFeedback(10, [{ id: 1 }]);

        expect(errorSpy).toHaveBeenCalledOnce();
        expect(errorSpy).toHaveBeenCalledWith('artemisApp.orion.assessment.submissionIdDontMatch');
    });

    it('openNextSubmission should call service', () => {
        const sendSubmissionToOrionCancellableSpy = jest.spyOn(orionAssessmentService, 'sendSubmissionToOrionCancellable');

        comp.openNextSubmission(2);

        expect(sendSubmissionToOrionCancellableSpy).toHaveBeenCalledOnce();
        expect(sendSubmissionToOrionCancellableSpy).toHaveBeenCalledWith(15, 2, 1, false);
    });
});
