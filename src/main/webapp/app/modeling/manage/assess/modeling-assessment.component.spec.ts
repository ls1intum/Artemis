import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { ApollonEditor, UMLDiagramType, UMLModel } from '@tumaet/apollon';
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

/**
 * Creates a v4 format UMLModel with populated nodes and edges from the v3 test model.
 * In the test environment, Apollon's model getter returns empty nodes/edges because
 * React doesn't fully render. This helper creates a properly populated v4 model
 * that can be used to mock apollonEditor.model for testing highlight and element count features.
 */
function createV4ModelWithNodes(): UMLModel {
    const v3Model = cloneDeep(testClassDiagram as any);
    const nodes: Record<string, any> = {};
    const edges: Record<string, any> = {};

    for (const [id, element] of Object.entries(v3Model.elements || {})) {
        nodes[id] = { ...(element as any), data: {} };
    }
    for (const [id, rel] of Object.entries(v3Model.relationships || {})) {
        edges[id] = { ...(rel as any), data: {} };
    }

    return {
        id: 'test-model',
        version: '4.0.0',
        title: 'Test',
        type: v3Model.type,
        nodes,
        edges,
        assessments: v3Model.assessments || {},
    } as unknown as UMLModel;
}

/**
 * Mocks the apollonEditor.model getter/setter to use the provided model.
 * This is necessary because in the test environment, Apollon doesn't fully render
 * and returns empty nodes/edges. By mocking the getter, we can test the component's
 * logic for updating highlights and element counts.
 */
function mockApollonEditorModel(apollonEditor: ApollonEditor, model: UMLModel): { getCapturedModel: () => UMLModel } {
    let capturedModel = model;
    Object.defineProperty(apollonEditor, 'model', {
        get: () => capturedModel,
        set: (newModel: UMLModel) => {
            capturedModel = newModel;
        },
        configurable: true,
    });
    return { getCapturedModel: () => capturedModel };
}

describe('ModelingAssessmentComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ModelingAssessmentComponent>;
    let comp: ModelingAssessmentComponent;
    let translatePipe: ArtemisTranslatePipe;

    // Element IDs from test class diagram
    const ELEMENT_ID_1 = 'b234e5cb-33e3-4957-ae04-f7990ce8571a'; // Package
    const ELEMENT_ID_2 = '2f67120e-b491-4222-beb1-79e87c2cf54d'; // Connected Class
    const RELATIONSHIP_ID = '5a9a4eb3-8281-4de4-b0f2-3e2f164574bd'; // First relationship

    const makeMockModel = () => cloneDeep(testClassDiagram as unknown as UMLModel);

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

        // The effect filters by reference != undefined (not by validating referenceId exists in model)
        // mockFeedbackWithReference has reference: 'reference' - included
        // mockFeedbackInvalid has reference: 'reference' - included (even though referenceId='4' does not exist in model)
        // mockFeedbackWithoutReference has no reference property - excluded
        expect(comp.referencedFeedbacks).toHaveLength(2);

        // Verify mockFeedbackWithReference is present by referenceId
        expect(comp.referencedFeedbacks.some((f) => f.referenceId === mockFeedbackWithReference.referenceId)).toBe(true);

        // Verify mockFeedbackInvalid is present by referenceId (has reference property, so included by effect)
        expect(comp.referencedFeedbacks.some((f) => f.referenceId === mockFeedbackInvalid.referenceId)).toBe(true);

        // Verify unreferenced feedback is NOT present (has no reference property)
        expect(comp.referencedFeedbacks.some((f) => f.text === mockFeedbackWithoutReference.text)).toBe(false);

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
        expect((Object.values(mockModel.assessments)[0] as any).dropInfo.instruction).toBe(mockFeedbackWithGradingInstruction.gradingInstruction);

        // toHaveBeenCalledTimes(5): 2 from calculateLabel() + 3 from calculateDropInfo()
        expect(spy).toHaveBeenCalledTimes(5);
    });

    it('should update element counts', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => {}); // prevent: findDOMNode is deprecated and will be removed in the next major release

        const mockModel = makeMockModel();
        const v4Model = createV4ModelWithNodes();

        // Create element counts for specific elements
        const elementCounts: ModelElementCount[] = [
            { elementId: ELEMENT_ID_1, numberOfOtherElements: 5 },
            { elementId: ELEMENT_ID_2, numberOfOtherElements: 3 },
        ];

        // Mock translatePipe to return a meaningful value so assessmentNote gets set
        const spy = vi.spyOn(translatePipe, 'transform').mockImplementation((key: string, params?: any) => {
            if (key === 'artemisApp.modelingAssessment.impactWarning' && params?.affectedSubmissionsCount) {
                return `Warning: ${params.affectedSubmissionsCount} other submissions`;
            }
            return key;
        });

        fixture.componentRef.setInput('umlModel', mockModel);
        fixture.componentRef.setInput('elementCounts', elementCounts);
        fixture.detectChanges();

        await comp.ngAfterViewInit();
        await fixture.whenStable();

        expect(comp.apollonEditor).toBeDefined();

        // Mock the apollonEditor.model to return our v4 model with populated nodes
        const { getCapturedModel } = mockApollonEditorModel(comp.apollonEditor!, v4Model);

        // Now call updateElementCounts which will iterate over model.nodes
        await (comp as any).updateElementCounts(elementCounts);

        // Verify the translate pipe was called with the correct translation key and parameters
        expect(spy).toHaveBeenCalledWith('artemisApp.modelingAssessment.impactWarning', { affectedSubmissionsCount: 5 });
        expect(spy).toHaveBeenCalledWith('artemisApp.modelingAssessment.impactWarning', { affectedSubmissionsCount: 3 });

        // Verify the assessmentNote was actually set on the nodes with the translated text
        const updatedModel = getCapturedModel();
        expect((updatedModel.nodes as any)[ELEMENT_ID_1].data.assessmentNote).toBe('Warning: 5 other submissions');
        expect((updatedModel.nodes as any)[ELEMENT_ID_2].data.assessmentNote).toBe('Warning: 3 other submissions');

        // Verify nodes not in elementCounts don't have assessmentNote set
        const otherNodeId = 'ccac14e5-c828-4afb-ab97-0fb2a67e77d6'; // Class In Package
        expect((updatedModel.nodes as any)[otherNodeId].data.assessmentNote).toBeUndefined();
    });

    it('should generate feedback from assessment', () => {
        const mockModel = makeMockModel();
        fixture.componentRef.setInput('umlModel', mockModel);
        fixture.componentRef.setInput('resultFeedbacks', [mockFeedbackWithGradingInstruction]);

        fixture.detectChanges();

        comp.generateFeedbackFromAssessment(Object.values(mockModel.assessments));
        expect(comp.elementFeedback.get(mockFeedbackWithGradingInstruction.referenceId!)).toEqual(mockFeedbackWithGradingInstruction);
    });

    it('should highlight elements', async () => {
        const highlightedElements = new Map<string, string>();
        highlightedElements.set(ELEMENT_ID_1, 'red');
        highlightedElements.set(RELATIONSHIP_ID, 'blue');

        const v4Model = createV4ModelWithNodes();

        fixture.componentRef.setInput('umlModel', makeMockModel());
        fixture.componentRef.setInput('highlightedElements', highlightedElements);

        fixture.detectChanges();
        await comp.ngAfterViewInit();
        await fixture.whenStable();

        expect(comp.apollonEditor).not.toBeNull();

        // Mock the apollonEditor.model to return our v4 model with populated nodes/edges
        const { getCapturedModel } = mockApollonEditorModel(comp.apollonEditor!, v4Model);

        // Call updateHighlightedElements which sets highlight property on nodes/edges
        await (comp as any).updateHighlightedElements(highlightedElements);

        // Verify the highlight property was actually set on the model elements
        const updatedModel = getCapturedModel();
        expect((updatedModel.nodes as any)[ELEMENT_ID_1].highlight).toBe('red');
        expect((updatedModel.edges as any)[RELATIONSHIP_ID].highlight).toBe('blue');

        // Verify elements not in highlightedElements don't have highlight set
        expect((updatedModel.nodes as any)[ELEMENT_ID_2].highlight).toBeUndefined();
    });

    it('should update model', async () => {
        // Test that model can be updated with a new model
        const initialModel = makeMockModel();
        fixture.componentRef.setInput('umlModel', initialModel);
        fixture.detectChanges();
        await comp.ngAfterViewInit();
        await new Promise((r) => setTimeout(r, 0));
        expect(comp.apollonEditor).not.toBeNull();

        // Verify initial model was set
        expect(comp.umlModel()).toBe(initialModel);

        // Create a modified model (same structure, different content)
        const newModel = makeMockModel();
        newModel.type = 'ClassDiagram';
        fixture.componentRef.setInput('umlModel', newModel);
        fixture.detectChanges();
        await fixture.whenStable();
        await new Promise((r) => setTimeout(r, 0));

        // Verify the component's input was updated
        expect(comp.umlModel()).toBe(newModel);
        expect(comp.umlModel()?.type).toBe('ClassDiagram');

        // Verify the apollon editor is still valid and has the correct diagram type
        const apollonModel = comp.apollonEditor!.model;
        expect(apollonModel.type).toBe(newModel.type);
    });

    it('should update highlighted elements', async () => {
        const initialHighlights = new Map<string, string>();
        initialHighlights.set(ELEMENT_ID_1, 'red');
        initialHighlights.set(ELEMENT_ID_2, 'blue');

        const v4Model = createV4ModelWithNodes();

        fixture.componentRef.setInput('umlModel', makeMockModel());
        fixture.componentRef.setInput('highlightedElements', initialHighlights);

        fixture.detectChanges();
        await comp.ngAfterViewInit();
        await fixture.whenStable();

        expect(comp.apollonEditor).not.toBeNull();

        // Mock the apollonEditor.model to return our v4 model with populated nodes/edges
        const { getCapturedModel } = mockApollonEditorModel(comp.apollonEditor!, v4Model);

        // Apply initial highlights
        await (comp as any).updateHighlightedElements(initialHighlights);

        let updatedModel = getCapturedModel();
        expect((updatedModel.nodes as any)[ELEMENT_ID_1].highlight).toBe('red');
        expect((updatedModel.nodes as any)[ELEMENT_ID_2].highlight).toBe('blue');

        // Now update with different highlights - only ELEMENT_ID_2 should be green
        const newHighlights = new Map<string, string>();
        newHighlights.set(ELEMENT_ID_2, 'green');

        await (comp as any).updateHighlightedElements(newHighlights);

        updatedModel = getCapturedModel();
        // ELEMENT_ID_1 should now have undefined highlight (removed)
        expect((updatedModel.nodes as any)[ELEMENT_ID_1].highlight).toBeUndefined();
        // ELEMENT_ID_2 should have green highlight (updated)
        expect((updatedModel.nodes as any)[ELEMENT_ID_2].highlight).toBe('green');
    });

    it('should update highlighted assessments first round', async () => {
        fixture.componentRef.setInput('highlightDifferences', true);
        fixture.componentRef.setInput('umlModel', makeMockModel());
        fixture.componentRef.setInput('resultFeedbacks', [mockFeedbackWithReference]);
        comp.referencedFeedbacks = [mockFeedbackWithReference];
        vi.spyOn(translatePipe, 'transform').mockReturnValue('Second correction round');

        fixture.detectChanges();
        await comp.ngAfterViewInit();
        await new Promise((r) => setTimeout(r, 0));

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
        await new Promise((r) => setTimeout(r, 0));

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
