import { SimpleChange } from '@angular/core';
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

describe('ModelingAssessmentComponent', () => {
    let fixture: ComponentFixture<ModelingAssessmentComponent>;
    let comp: ModelingAssessmentComponent;
    let translatePipe: ArtemisTranslatePipe;

    const generateMockModel = (elementId: string, elementId2: string, relationshipId: string) => {
        return {
            version: '4.0.0',
            id: 'model-id',
            title: 'Class Diagram',
            type: UMLDiagramType.ClassDiagram,
            nodes: [
                {
                    id: elementId,
                    width: 200,
                    height: 100,
                    type: 'Package',
                    position: { x: 160, y: 40 },
                    data: { name: 'Package' },
                    measured: { width: 200, height: 100 },
                },
                {
                    id: elementId2,
                    width: 200,
                    height: 100,
                    type: 'Class',
                    position: { x: 280, y: 320 },
                    data: { name: 'Class', attributes: [], methods: [] },
                    measured: { width: 200, height: 100 },
                },
            ],
            edges: [
                {
                    id: relationshipId,
                    source: elementId2,
                    target: elementId,
                    type: 'ClassBidirectional',
                    sourceHandle: 'source',
                    targetHandle: 'target',
                    data: { points: [] },
                },
            ],
            assessments: {},
        } as unknown as UMLModel;
    };

    const makeMockModel = () => generateMockModel('elementId1', 'elementId2', 'relationshipId');
    const mockFeedbackWithReference: Feedback = {
        text: 'FeedbackWithReference',
        referenceId: 'relationshipId',
        reference: 'reference',
        referenceType: 'ClassBidirectional',
        credits: 30,
        correctionStatus: 'CORRECT',
    };
    const mockFeedbackWithReferenceCopied: Feedback = {
        text: 'FeedbackWithReference Copied',
        referenceId: 'relationshipId',
        reference: 'reference',
        referenceType: 'ClassBidirectional',
        credits: 35,
        copiedFeedbackId: 12,
    };
    const mockFeedbackWithoutReference: Feedback = { text: 'FeedbackWithoutReference', credits: 30, type: FeedbackType.MANUAL_UNREFERENCED };
    const mockFeedbackInvalid: Feedback = {
        text: 'FeedbackInvalid',
        referenceId: '4',
        reference: 'reference',
        referenceType: 'ClassBidirectional',
        correctionStatus: FeedbackCorrectionErrorType.INCORRECT_SCORE,
    };
    const mockValidFeedbacks = [mockFeedbackWithReference, mockFeedbackWithoutReference];
    const mockFeedbacks = [...mockValidFeedbacks, mockFeedbackInvalid];

    const mockFeedbackWithGradingInstruction: Feedback = {
        text: 'FeedbackWithGradingInstruction',
        referenceId: 'relationshipId',
        reference: 'reference',
        referenceType: 'ClassBidirectional',
        credits: 30,
        gradingInstruction: new GradingInstruction(),
    };
    mockFeedbackWithGradingInstruction.gradingInstruction!.instructionDescription = 'instruction description';

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(FormsModule)],
            declarations: [ModelingAssessmentComponent, ScoreDisplayComponent, ModelingExplanationEditorComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(ArtemisTranslatePipe), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ModelingAssessmentComponent);
                comp = fixture.componentInstance;
                translatePipe = TestBed.inject(ArtemisTranslatePipe);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should show title if any', () => {
        const title = 'Test Title';
        comp.title = title;
        fixture.detectChanges();
        const el = fixture.debugElement.query((de) => de.nativeElement.textContent === title);
        expect(el).not.toBeNull();
    });

    describe('score display', () => {
        let totalScore: number;
        let maxScore: number;
        beforeEach(() => {
            totalScore = 40;
            comp.totalScore = totalScore;
            maxScore = 66;
            comp.maxScore = maxScore;
        });
        it('should display score display with right values', () => {
            comp.displayPoints = true;
            fixture.detectChanges();
            const scoreDisplay = fixture.debugElement.query(By.directive(ScoreDisplayComponent));
            expect(scoreDisplay).not.toBeNull();
            expect(scoreDisplay.componentInstance.score).toEqual(totalScore);
            expect(scoreDisplay.componentInstance.maxPoints).toEqual(maxScore);
        });

        it('should not display score if displayPoints wrong', () => {
            comp.displayPoints = false;
            fixture.detectChanges();
            const scoreDisplay = fixture.debugElement.query(By.directive(ScoreDisplayComponent));
            expect(scoreDisplay).toBeNull();
        });
    });

    it('should display explanation editor if there is an explanation', () => {
        const explanation = 'Explanation';
        comp.explanation = explanation;
        fixture.detectChanges();
        const explanationEditor = fixture.debugElement.query(By.directive(ModelingExplanationEditorComponent));
        expect(explanationEditor).not.toBeNull();
        expect(explanationEditor.componentInstance.explanation).toEqual(explanation);
        expect(explanationEditor.componentInstance.readOnly).toBeTrue();
    });

    it('should initialize apollon editor', () => {
        comp.umlModel = makeMockModel();
        comp.diagramType = UMLDiagramType.ClassDiagram;
        fixture.detectChanges();
        expect(comp.apollonEditor).not.toBeNull();
    });

    it('should filter references', () => {
        comp.umlModel = makeMockModel();
        comp.readOnly = true;
        comp.feedbacks = mockFeedbacks;
        fixture.detectChanges();
        expect(comp.referencedFeedbacks).toEqual([mockFeedbackWithReference]);
        expect(comp.unreferencedFeedbacks).toEqual([mockFeedbackWithoutReference]);
        expect(comp.feedbacks).toEqual(mockFeedbacks);
    });

    it('should filter references by result feedbacks', () => {
        expect(comp.referencedFeedbacks).toBeEmpty();
        expect(comp.feedbacks).toBeUndefined();

        comp.umlModel = makeMockModel();
        comp.resultFeedbacks = mockFeedbacks;

        expect(comp.referencedFeedbacks).toEqual([mockFeedbackWithReference, mockFeedbackInvalid]);
        expect(comp.feedbacks).toEqual(mockFeedbacks);
    });

    it('should calculate drop info', () => {
        const spy = jest.spyOn(translatePipe, 'transform');
        const mockModel = makeMockModel();
        comp.umlModel = mockModel;
        comp.resultFeedbacks = [mockFeedbackWithGradingInstruction];
        const assessments: any[] = Object.values(mockModel.assessments) as any[];

        expect(spy).toHaveBeenCalledWith('artemisApp.assessment.messages.removeAssessmentInstructionLink');
        expect(spy).toHaveBeenCalledWith('artemisApp.exercise.assessmentInstruction');
        expect(spy).toHaveBeenCalledWith('artemisApp.assessment.feedbackHint');
        expect(assessments[0].dropInfo.instruction).toBe(mockFeedbackWithGradingInstruction.gradingInstruction);

        // toHaveBeenCalledTimes(5): 2 from calculateLabel() + 3 from calculateDropInfo()
        expect(spy).toHaveBeenCalledTimes(5);
    });

    it('should update element counts', async () => {
        jest.spyOn(console, 'error').mockImplementation(); // prevent: findDOMNode is deprecated and will be removed in the next major release
        function getElementCounts(model: UMLModel): ModelElementCount[] {
            // Not sure whether this is the correct logic to build OtherModelElementCounts.
            return model.nodes.map((node) => ({
                elementId: node.id,
                numberOfOtherElements: model.nodes.length - 1,
            }));
        }

        const mockModel = makeMockModel();
        comp.umlModel = mockModel;
        const elementCounts = getElementCounts(mockModel);
        comp.elementCounts = elementCounts;
        const spy = jest.spyOn(translatePipe, 'transform');
        fixture.detectChanges();
        await fixture.whenStable();
        const impactWarningCalls = spy.mock.calls.filter(([key]) => key === 'artemisApp.modelingAssessment.impactWarning');
        for (let i = 0; i < elementCounts.length; i++) {
            expect(impactWarningCalls).toContainEqual(['artemisApp.modelingAssessment.impactWarning', { affectedSubmissionsCount: elementCounts[i].numberOfOtherElements }]);
        }
        expect(impactWarningCalls).toHaveLength(elementCounts.length);
    });

    it('should generate feedback from assessment', () => {
        const mockModel = makeMockModel();
        comp.umlModel = mockModel;
        comp.resultFeedbacks = [mockFeedbackWithGradingInstruction];

        fixture.detectChanges();

        comp.generateFeedbackFromAssessment(Object.values(mockModel.assessments));
        expect(comp.elementFeedback.get(mockFeedbackWithGradingInstruction.referenceId!)).toEqual(mockFeedbackWithGradingInstruction);
    });

    it('should highlight elements', async () => {
        const highlightedElements = new Map();
        highlightedElements.set('elementId1', 'red');
        highlightedElements.set('relationshipId', 'blue');
        comp.umlModel = makeMockModel();
        comp.highlightedElements = highlightedElements;

        fixture.detectChanges();
        await fixture.whenStable();
        await (comp.apollonEditor as any).nextRender;
        expect(comp.apollonEditor).not.toBeNull();

        const apollonModel = comp.apollonEditor!.model;
        const highlightedElement = apollonModel.nodes.find((node: any) => node.id === 'elementId1');
        const notHighlightedElement = apollonModel.nodes.find((node: any) => node.id === 'elementId2');
        const relationship = apollonModel.edges.find((edge: any) => edge.id === 'relationshipId');
        expect(highlightedElement).not.toBeNull();
        expect(highlightedElement!.data.highlight).toBe('red');
        expect(notHighlightedElement).not.toBeNull();
        expect(notHighlightedElement!.data.highlight).toBeUndefined();
        expect(relationship).not.toBeNull();
        expect(relationship!.data.highlight).toBe('blue');
    });

    it('should update model', async () => {
        const newModel = generateMockModel('newElement1', 'newElement2', 'newRelationship');
        const changes = { umlModel: { currentValue: newModel } as SimpleChange };
        fixture.detectChanges();
        const apollonSpy = jest.spyOn(comp.apollonEditor!, 'model', 'set');
        await fixture.whenStable();
        await (comp.apollonEditor as any).nextRender;
        await comp.ngOnChanges(changes);
        expect(apollonSpy).toHaveBeenCalledWith(newModel);
    });

    it('should update highlighted elements', async () => {
        const highlightedElements = new Map();
        highlightedElements.set('elementId2', 'green');
        const changes = { highlightedElements: { currentValue: highlightedElements } as SimpleChange };
        comp.umlModel = makeMockModel();
        comp.highlightedElements = highlightedElements;

        fixture.detectChanges();
        await fixture.whenStable();
        await (comp.apollonEditor as any).nextRender;
        await comp.ngOnChanges(changes);

        expect(comp.apollonEditor).not.toBeNull();
        const apollonModel = comp.apollonEditor!.model;
        const highlightedElement = apollonModel.nodes.find((node: any) => node.id === 'elementId2');
        const notHighlightedElement = apollonModel.nodes.find((node: any) => node.id === 'elementId1');
        const relationship = comp.apollonEditor!.model.edges.find((edge: any) => edge.id === 'relationshipId');

        expect(highlightedElement).not.toBeNull();
        expect(highlightedElement!.data.highlight).toBe('green');
        expect(notHighlightedElement).not.toBeNull();
        expect(notHighlightedElement!.data.highlight).toBeUndefined();
        expect(relationship).not.toBeNull();
        expect(relationship!.data.highlight).toBeUndefined();
    });

    it('should update highlighted assessments first round', async () => {
        const changes = { highlightDifferences: { currentValue: true } as SimpleChange };
        comp.highlightDifferences = true;
        comp.umlModel = makeMockModel();
        comp.feedbacks = [mockFeedbackWithReference];
        comp.referencedFeedbacks = [mockFeedbackWithReference];
        jest.spyOn(translatePipe, 'transform').mockReturnValue('Second correction round');

        fixture.detectChanges();
        await fixture.whenStable();
        await comp.ngOnChanges(changes);
        expect(comp.apollonEditor).toBeDefined();

        await (comp.apollonEditor as any).nextRender;
        const apollonModel = comp.apollonEditor!.model;
        const assessments: any = Object.values(apollonModel.assessments);
        expect(assessments[0].labelColor).toEqual(comp.secondCorrectionRoundColor);
        expect(assessments[0].label).toBe('Second correction round');
        expect(assessments[0].score).toBe(30);
    });

    it('should update highlighted assessments', async () => {
        const changes = { highlightDifferences: { currentValue: true } as SimpleChange };
        comp.highlightDifferences = true;
        comp.umlModel = makeMockModel();

        fixture.detectChanges();
        comp.feedbacks = [mockFeedbackWithReferenceCopied];
        comp.referencedFeedbacks = [mockFeedbackWithReferenceCopied];
        jest.spyOn(translatePipe, 'transform').mockReturnValue('First correction round');

        await comp.ngOnChanges(changes);
        await fixture.whenStable();
        await (comp.apollonEditor as any).nextRender;

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
            referenceId: 'relationshipId',
            reference: 'reference',
            referenceType: 'ClassBidirectional',
            credits: 30,
        } as Feedback;
        const newMockFeedbackWithoutReference = { text: 'NewFeedbackWithoutReference', credits: 30, type: FeedbackType.MANUAL_UNREFERENCED } as Feedback;
        const newMockFeedbackInvalid = { text: 'NewFeedbackInvalid', referenceId: '4', reference: 'reference', referenceType: 'ClassBidirectional' };
        const newMockValidFeedbacks = [newMockFeedbackWithReference, newMockFeedbackWithoutReference];
        const newMockFeedbacks = [...newMockValidFeedbacks, newMockFeedbackInvalid];
        comp.umlModel = makeMockModel();
        comp.readOnly = true;
        fixture.detectChanges();
        const changes = { feedbacks: { currentValue: newMockFeedbacks } as SimpleChange };
        comp.ngOnChanges(changes);
        expect(comp.feedbacks).toEqual(newMockFeedbacks);
        expect(comp.referencedFeedbacks).toEqual([newMockFeedbackWithReference]);
    });
});
