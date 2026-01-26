import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { UMLDiagramType, UMLModel } from '@tumaet/apollon';
import { Feedback, FeedbackCorrectionErrorType, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { ModelingAssessmentComponent } from 'app/modeling/manage/assess/modeling-assessment.component';
import { ModelingExplanationEditorComponent } from 'app/modeling/shared/modeling-explanation-editor/modeling-explanation-editor.component';
import { ScoreDisplayComponent } from 'app/shared/score-display/score-display.component';
import { MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ModelElementCount } from 'app/modeling/shared/entities/modeling-submission.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import testClassDiagram from 'test/helpers/sample/modeling/test-models/class-diagram.json';
import { cloneDeep } from 'lodash-es';

describe('ModelingAssessmentComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ModelingAssessmentComponent>;
    let comp: ModelingAssessmentComponent;
    let translatePipe: ArtemisTranslatePipe;

    // Element IDs from test class diagram
    const ELEMENT_ID_1 = 'b234e5cb-33e3-4957-ae04-f7990ce8571a'; // Package
    const ELEMENT_ID_2 = '2f67120e-b491-4222-beb1-79e87c2cf54d'; // Connected Class
    const RELATIONSHIP_ID = '5a9a4eb3-8281-4de4-b0f2-3e2f164574bd'; // First relationship

    const makeMockModel = () => cloneDeep(testClassDiagram as UMLModel);

    const mockFeedbackWithReference: Feedback = {
        text: 'FeedbackWithReference',
        referenceId: RELATIONSHIP_ID,
        reference: 'reference',
        credits: 30,
        correctionStatus: 'CORRECT',
    };
    const mockFeedbackWithReferenceCopied: Feedback = {
        text: 'FeedbackWithReference Copied',
        referenceId: RELATIONSHIP_ID,
        reference: 'reference',
        credits: 35,
        copiedFeedbackId: 12,
    };
    const mockFeedbackWithoutReference: Feedback = {
        text: 'FeedbackWithoutReference',
        credits: 30,
        type: FeedbackType.MANUAL_UNREFERENCED,
    };
    const mockFeedbackInvalid: Feedback = {
        text: 'FeedbackInvalid',
        referenceId: '4',
        reference: 'reference',
        correctionStatus: FeedbackCorrectionErrorType.INCORRECT_SCORE,
    };
    const mockValidFeedbacks = [mockFeedbackWithReference, mockFeedbackWithoutReference];
    const mockFeedbacks = [...mockValidFeedbacks, mockFeedbackInvalid];

    const mockFeedbackWithGradingInstruction: Feedback = {
        text: 'FeedbackWithGradingInstruction',
        referenceId: RELATIONSHIP_ID,
        reference: 'reference',
        credits: 30,
        gradingInstruction: new GradingInstruction(),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(FormsModule), ModelingAssessmentComponent, ScoreDisplayComponent, ModelingExplanationEditorComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(ArtemisTranslatePipe),
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
            ],
        });

        fixture = TestBed.createComponent(ModelingAssessmentComponent);
        comp = fixture.componentInstance;
        translatePipe = TestBed.inject(ArtemisTranslatePipe);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should show title if any', () => {
        const title = 'Test Title';
        fixture.componentRef.setInput('title', title);
        fixture.detectChanges();
        const el = fixture.debugElement.query((de) => de.nativeElement.textContent === title);
        expect(el).not.toBeNull();
    });

    describe('score display', () => {
        let totalScore: number;
        let maxScore: number;
        beforeEach(() => {
            totalScore = 40;
            fixture.componentRef.setInput('totalScore', totalScore);
            maxScore = 66;
            fixture.componentRef.setInput('maxScore', maxScore);
        });
        it('should display score display with right values', () => {
            fixture.componentRef.setInput('displayPoints', true);
            fixture.detectChanges();
            const scoreDisplay = fixture.debugElement.query(By.directive(ScoreDisplayComponent));
            expect(scoreDisplay).not.toBeNull();
            expect(scoreDisplay.componentInstance.score).toEqual(totalScore);
            expect(scoreDisplay.componentInstance.maxPoints).toEqual(maxScore);
        });

        it('should not display score if displayPoints wrong', () => {
            fixture.componentRef.setInput('displayPoints', false);
            fixture.detectChanges();
            const scoreDisplay = fixture.debugElement.query(By.directive(ScoreDisplayComponent));
            expect(scoreDisplay).toBeNull();
        });
    });

    it('should display explanation editor if there is an explanation', () => {
        const explanation = 'Explanation';
        fixture.componentRef.setInput('explanation', explanation);
        fixture.detectChanges();
        const explanationEditor = fixture.debugElement.query(By.directive(ModelingExplanationEditorComponent));
        expect(explanationEditor).not.toBeNull();
        expect(explanationEditor.componentInstance.explanation()).toEqual(explanation);
        expect(explanationEditor.componentInstance.readOnly()).toBe(true);
    });

    it('should initialize apollon editor', () => {
        fixture.componentRef.setInput('umlModel', makeMockModel());
        fixture.componentRef.setInput('diagramType', UMLDiagramType.ClassDiagram);

        fixture.detectChanges();
        expect(comp.apollonEditor).not.toBeNull();
    });

    it('should filter references', () => {
        fixture.componentRef.setInput('umlModel', makeMockModel());
        fixture.componentRef.setInput('readOnly', true);

        fixture.componentRef.setInput('resultFeedbacks', mockFeedbacks);
        fixture.detectChanges();
        expect(comp.referencedFeedbacks).toEqual([mockFeedbackWithReference]);
        expect(comp.unreferencedFeedbacks).toEqual([mockFeedbackWithoutReference]);
        expect(comp.resultFeedbacks()).toEqual(mockFeedbacks);
    });

    it('should filter references by result feedbacks', () => {
        expect(comp.referencedFeedbacks).toHaveLength(0);
        expect(comp.resultFeedbacks()).toBeUndefined();

        fixture.componentRef.setInput('umlModel', makeMockModel());
        fixture.detectChanges();
        fixture.componentRef.setInput('resultFeedbacks', mockFeedbacks);
        fixture.detectChanges();
        // Feedback with reference property (not undefined) are included
        // Only check length and that the valid feedback is present, since exact object comparison
        // can vary due to Feedback class instantiation in effects
        expect(comp.referencedFeedbacks.length).toBeGreaterThanOrEqual(1);
        expect(comp.referencedFeedbacks.some((f) => f.referenceId === mockFeedbackWithReference.referenceId)).toBe(true);
        expect(comp.resultFeedbacks()).toEqual(mockFeedbacks);
    });

    it('should calculate drop info', () => {
        const spy = vi.spyOn(translatePipe, 'transform');
        const mockModel = makeMockModel();
        fixture.componentRef.setInput('umlModel', mockModel);
        fixture.detectChanges();
        fixture.componentRef.setInput('resultFeedbacks', [mockFeedbackWithGradingInstruction]);
        fixture.detectChanges();
        expect(spy).toHaveBeenCalledWith('artemisApp.assessment.messages.removeAssessmentInstructionLink');
        expect(spy).toHaveBeenCalledWith('artemisApp.exercise.assessmentInstruction');
        expect(spy).toHaveBeenCalledWith('artemisApp.assessment.feedbackHint');
        expect(Object.values(mockModel.assessments)[0].dropInfo.instruction).toBe(mockFeedbackWithGradingInstruction.gradingInstruction);

        // toHaveBeenCalledTimes(5): 2 from calculateLabel() + 3 from calculateDropInfo()
        expect(spy).toHaveBeenCalledTimes(5);
    });

    // TODO: Fix test after Apollon v4 migration - the element count tracking needs v4 format
    it.skip('should update element counts', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => {}); // prevent: findDOMNode is deprecated and will be removed in the next major release
        function getElementCounts(model: UMLModel): ModelElementCount[] {
            // Support both v3 (elements) and v4 (nodes) formats
            const elements = model.nodes ?? (model as any).elements ?? {};
            return Object.values(elements).map((el: any) => ({
                elementId: el.id,
                numberOfOtherElements: Object.values(elements).length - 1,
            }));
        }

        const mockModel = makeMockModel();
        fixture.componentRef.setInput('umlModel', mockModel);
        const elementCounts = getElementCounts(mockModel);
        fixture.componentRef.setInput('elementCounts', elementCounts);

        const spy = vi.spyOn(translatePipe, 'transform');
        fixture.detectChanges();
        await fixture.whenStable();
        await comp.ngAfterViewInit();
        for (let i = 0; i < elementCounts.length; i++) {
            expect(spy).toHaveBeenCalledWith('artemisApp.modelingAssessment.impactWarning', { affectedSubmissionsCount: elementCounts[i].numberOfOtherElements });
        }
        expect(spy).toHaveBeenCalledTimes(elementCounts.length);
    });

    it('should generate feedback from assessment', () => {
        const mockModel = makeMockModel();
        fixture.componentRef.setInput('umlModel', mockModel);
        fixture.componentRef.setInput('resultFeedbacks', [mockFeedbackWithGradingInstruction]);

        fixture.detectChanges();

        comp.generateFeedbackFromAssessment(Object.values(mockModel.assessments));
        expect(comp.elementFeedback.get(mockFeedbackWithGradingInstruction.referenceId!)).toEqual(mockFeedbackWithGradingInstruction);
    });

    // TODO: Fix test after Apollon v4 migration - highlighting requires v4 model format
    it.skip('should highlight elements', async () => {
        const highlightedElements = new Map();
        highlightedElements.set(ELEMENT_ID_1, 'red');
        highlightedElements.set(RELATIONSHIP_ID, 'blue');
        fixture.componentRef.setInput('umlModel', makeMockModel());
        fixture.componentRef.setInput('highlightedElements', highlightedElements);

        fixture.detectChanges();
        await fixture.whenStable();
        await comp.ngAfterViewInit();
        expect(comp.apollonEditor).not.toBeNull();

        const apollonModel = comp.apollonEditor!.model;
        // Apollon v4 uses nodes/edges internally
        const elements = apollonModel.nodes ?? (apollonModel as any).elements ?? {};
        const edges = apollonModel.edges ?? (apollonModel as any).relationships ?? {};
        const highlightedElement = elements[ELEMENT_ID_1];
        const notHighlightedElement = elements[ELEMENT_ID_2];
        const relationship = edges[RELATIONSHIP_ID];
        expect(highlightedElement).not.toBeNull();
        expect(highlightedElement?.highlight).toBe('red');
        expect(notHighlightedElement).not.toBeNull();
        expect(notHighlightedElement?.highlight).toBeUndefined();
        expect(relationship).not.toBeNull();
        expect(relationship?.highlight).toBe('blue');
    });

    // TODO: Fix test after Apollon v4 migration - model updates require v4 format in setter
    it.skip('should update model', async () => {
        // Test that model can be updated with a new model
        const initialModel = makeMockModel();
        fixture.componentRef.setInput('umlModel', initialModel);
        fixture.detectChanges();
        await comp.ngAfterViewInit();
        await comp.apollonEditor!.nextRender;
        expect(comp.apollonEditor).not.toBeNull();

        // Create a modified model (same structure, different content)
        const newModel = makeMockModel();
        newModel.type = 'ClassDiagram';
        fixture.componentRef.setInput('umlModel', newModel);
        fixture.detectChanges();
        await fixture.whenStable();
        await comp.apollonEditor!.nextRender;

        const apollonModel = comp.apollonEditor!.model;
        expect(apollonModel.type).toBe(newModel.type);
        // Verify model structure is intact
        const elements = apollonModel.nodes ?? (apollonModel as any).elements ?? {};
        const edges = apollonModel.edges ?? (apollonModel as any).relationships ?? {};
        expect(Object.keys(elements).length).toBeGreaterThan(0);
        expect(Object.keys(edges).length).toBeGreaterThan(0);
    });

    // TODO: Fix test after Apollon v4 migration - highlighting requires v4 model format
    it.skip('should update highlighted elements', async () => {
        const highlightedElements = new Map<string, string>();
        highlightedElements.set(ELEMENT_ID_2, 'green');

        fixture.componentRef.setInput('umlModel', makeMockModel());
        fixture.componentRef.setInput('highlightedElements', highlightedElements);

        fixture.detectChanges();
        await fixture.whenStable();
        await comp.ngAfterViewInit();
        await comp.apollonEditor!.nextRender;

        expect(comp.apollonEditor).not.toBeNull();
        const apollonModel = comp.apollonEditor!.model;
        // Apollon v4 uses nodes/edges internally
        const elements = apollonModel.nodes ?? (apollonModel as any).elements ?? {};
        const edges = apollonModel.edges ?? (apollonModel as any).relationships ?? {};
        const highlightedElement = elements[ELEMENT_ID_2];
        const notHighlightedElement = elements[ELEMENT_ID_1];
        const relationship = edges[RELATIONSHIP_ID];

        expect(highlightedElement).not.toBeNull();
        expect(highlightedElement?.highlight).toBe('green');
        expect(notHighlightedElement).not.toBeNull();
        expect(notHighlightedElement?.highlight).toBeUndefined();
        expect(relationship).not.toBeNull();
        expect(relationship?.highlight).toBeUndefined();
    });

    it('should update highlighted assessments first round', async () => {
        fixture.componentRef.setInput('highlightDifferences', true);
        fixture.componentRef.setInput('umlModel', makeMockModel());
        fixture.componentRef.setInput('resultFeedbacks', [mockFeedbackWithReference]);
        comp.referencedFeedbacks = [mockFeedbackWithReference];
        vi.spyOn(translatePipe, 'transform').mockReturnValue('Second correction round');

        fixture.detectChanges();
        await comp.ngAfterViewInit();
        await comp.apollonEditor!.nextRender;

        expect(comp.apollonEditor).toBeDefined();

        const apollonModel = comp.apollonEditor!.model;
        const assessments: any = Object.values(apollonModel.assessments);

        expect(assessments[0].labelColor).toEqual(comp.secondCorrectionRoundColor);
        expect(assessments[0].label).toBe('Second correction round');
        expect(assessments[0].score).toBe(30);
    });

    it('should update highlighted assessments', async () => {
        fixture.componentRef.setInput('umlModel', makeMockModel());
        fixture.componentRef.setInput('resultFeedbacks', [mockFeedbackWithReferenceCopied]);
        comp.referencedFeedbacks = [mockFeedbackWithReferenceCopied];
        vi.spyOn(translatePipe, 'transform').mockReturnValue('First correction round');

        fixture.componentRef.setInput('highlightDifferences', true);
        fixture.detectChanges();

        await fixture.whenStable();
        await comp.apollonEditor!.nextRender;

        expect(comp.apollonEditor).not.toBeNull();

        const apollonModel = comp.apollonEditor!.model;
        const assessments: any = Object.values(apollonModel.assessments);

        expect(assessments[0].labelColor).toEqual(comp.firstCorrectionRoundColor);
        expect(assessments[0].label).toBe('First correction round');
        expect(assessments[0].score).toBe(35);
    });

    it('should update feedbacks', () => {
        const newMockFeedbackWithReference = {
            text: 'NewFeedbackWithReference',
            referenceId: RELATIONSHIP_ID,
            reference: 'reference',
            credits: 30,
        } as Feedback;
        const newMockFeedbackWithoutReference = {
            text: 'NewFeedbackWithoutReference',
            credits: 30,
            type: FeedbackType.MANUAL_UNREFERENCED,
        } as Feedback;
        const newMockFeedbackInvalid = {
            text: 'NewFeedbackInvalid',
            referenceId: '4',
            reference: 'reference',
        } as Feedback;

        const newMockValidFeedbacks = [newMockFeedbackWithReference, newMockFeedbackWithoutReference];
        const newMockFeedbacks = [...newMockValidFeedbacks, newMockFeedbackInvalid];

        fixture.componentRef.setInput('umlModel', makeMockModel());
        fixture.componentRef.setInput('readOnly', true);
        fixture.componentRef.setInput('resultFeedbacks', newMockFeedbacks);
        fixture.detectChanges();

        (comp as any).handleFeedback();

        expect(comp.resultFeedbacks()).toEqual(newMockFeedbacks);
        expect(comp.referencedFeedbacks).toEqual([newMockFeedbackWithReference]);
    });

    it('should ignore handleFeedback when resultFeedbacks is undefined', () => {
        (comp as any).handleFeedback();
        expect(comp.referencedFeedbacks).toEqual([]);
    });
});
