import { SimpleChange } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { UMLDiagramType, UMLElement, UMLModel, UMLRelationship } from '@ls1intum/apollon';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { Feedback, FeedbackCorrectionErrorType, FeedbackType } from 'app/entities/feedback.model';
import { ModelElementCount } from 'app/entities/modeling-submission.model';
import { ModelingAssessmentComponent } from 'app/exercises/modeling/assess/modeling-assessment.component';
import { ModelingExplanationEditorComponent } from 'app/exercises/modeling/shared/modeling-explanation-editor.component';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ScoreDisplayComponent } from 'app/shared/score-display/score-display.component';
import { MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';

describe('ModelingAssessmentComponent', () => {
    let fixture: ComponentFixture<ModelingAssessmentComponent>;
    let comp: ModelingAssessmentComponent;
    let translatePipe: ArtemisTranslatePipe;

    const generateMockModel = (elementId: string, elementId2: string, relationshipId: string) => {
        return {
            version: '2.0.0',
            type: 'ClassDiagram',
            size: { width: 740, height: 660 },
            interactive: { elements: [], relationships: [] },
            elements: [
                {
                    id: elementId,
                    name: 'Package',
                    type: 'Package',
                    owner: null,
                    bounds: { x: 160, y: 40, width: 200, height: 100 },
                    highlight: undefined,
                },
                {
                    id: elementId2,
                    name: 'Class',
                    type: 'Class',
                    owner: null,
                    bounds: { x: 280, y: 320, width: 200, height: 100 },
                    attributes: [],
                    methods: [],
                    highlight: undefined,
                },
            ],
            relationships: [
                {
                    id: relationshipId,
                    name: '',
                    type: 'ClassBidirectional',
                    owner: null,
                    bounds: { x: 120, y: 0, width: 265, height: 320 },
                    path: [
                        { x: 260, y: 320 },
                        { x: 260, y: 280 },
                        { x: 0, y: 280 },
                        { x: 0, y: 0 },
                        { x: 140, y: 0 },
                        { x: 140, y: 40 },
                    ],
                    source: {
                        direction: 'Up',
                        element: elementId2,
                    },
                    target: {
                        direction: 'Up',
                        element: elementId,
                    },
                } as UMLRelationship,
            ],
            assessments: [],
        } as UMLModel;
    };

    const mockModel = generateMockModel('elementId1', 'elementId2', 'relationshipId');
    const mockFeedbackWithReference = {
        text: 'FeedbackWithReference',
        referenceId: 'relationshipId',
        reference: 'reference',
        credits: 30,
        correctionStatus: 'CORRECT',
    } as Feedback;
    const mockFeedbackWithReferenceCopied = {
        text: 'FeedbackWithReference Copied',
        referenceId: 'relationshipId',
        reference: 'reference',
        credits: 35,
        copiedFeedbackId: 12,
    } as Feedback;
    const mockFeedbackWithoutReference = { text: 'FeedbackWithoutReference', credits: 30, type: FeedbackType.MANUAL_UNREFERENCED } as Feedback;
    const mockFeedbackInvalid = { text: 'FeedbackInvalid', referenceId: '4', reference: 'reference', correctionStatus: FeedbackCorrectionErrorType.INCORRECT_SCORE };
    const mockValidFeedbacks = [mockFeedbackWithReference, mockFeedbackWithoutReference];
    const mockFeedbacks = [...mockValidFeedbacks, mockFeedbackInvalid];

    const mockFeedbackWithGradingInstruction = {
        text: 'FeedbackWithGradingInstruction',
        referenceId: 'relationshipId',
        reference: 'reference',
        credits: 30,
        gradingInstruction: new GradingInstruction(),
    } as Feedback;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(FormsModule)],
            declarations: [ModelingAssessmentComponent, ScoreDisplayComponent, ModelingExplanationEditorComponent, MockDirective(NgbTooltip), MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(ArtemisTranslatePipe)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ModelingAssessmentComponent);
                comp = fixture.componentInstance;
                translatePipe = fixture.debugElement.injector.get(ArtemisTranslatePipe);
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
        comp.umlModel = mockModel;
        comp.diagramType = UMLDiagramType.ClassDiagram;
        fixture.detectChanges();
        expect(comp.apollonEditor).not.toBeNull();
    });

    it('should filter references', () => {
        comp.umlModel = mockModel;
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

        comp.umlModel = mockModel;
        comp.resultFeedbacks = mockFeedbacks;

        expect(comp.referencedFeedbacks).toEqual([mockFeedbackWithReference, mockFeedbackInvalid]);
        expect(comp.feedbacks).toEqual(mockFeedbacks);
    });

    it('should calculate drop info', () => {
        const spy = jest.spyOn(translatePipe, 'transform');
        comp.umlModel = mockModel;
        comp.resultFeedbacks = [mockFeedbackWithGradingInstruction];

        expect(spy).toHaveBeenCalledWith('artemisApp.assessment.messages.removeAssessmentInstructionLink');
        expect(spy).toHaveBeenCalledWith('artemisApp.exercise.assessmentInstruction');
        expect(spy).toHaveBeenCalledWith('artemisApp.assessment.feedbackHint');
        expect(mockModel.assessments[0].dropInfo.instruction).toBe(mockFeedbackWithGradingInstruction.gradingInstruction);

        // toHaveBeenCalledTimes(5): 2 from calculateLabel() + 3 from calculateDropInfo()
        expect(spy).toHaveBeenCalledTimes(5);
    });

    it('should update element counts', () => {
        function getElementCounts(model: UMLModel): ModelElementCount[] {
            // Not sure whether this is the correct logic to build OtherModelElementCounts.
            return model.elements.map((el) => ({
                elementId: el.id,
                numberOfOtherElements: model.elements.length - 1,
            }));
        }

        comp.umlModel = mockModel;
        const elementCounts = getElementCounts(mockModel);
        comp.elementCounts = elementCounts;

        const spy = jest.spyOn(translatePipe, 'transform');

        fixture.detectChanges();

        elementCounts.forEach((elementCount) =>
            expect(spy).toHaveBeenCalledWith('artemisApp.modelingAssessment.impactWarning', { affectedSubmissionsCount: elementCount.numberOfOtherElements }),
        );

        expect(spy).toHaveBeenCalledTimes(elementCounts.length);
    });

    it('should generate feedback from assessment', () => {
        comp.umlModel = mockModel;
        comp.resultFeedbacks = [mockFeedbackWithGradingInstruction];

        fixture.detectChanges();

        comp.generateFeedbackFromAssessment(mockModel.assessments);
        expect(comp.elementFeedback.get(mockFeedbackWithGradingInstruction.referenceId!)).toEqual(mockFeedbackWithGradingInstruction);
    });

    it('should highlight elements', () => {
        const highlightedElements = new Map();
        highlightedElements.set('elementId1', 'red');
        highlightedElements.set('relationshipId', 'blue');
        comp.umlModel = mockModel;
        comp.highlightedElements = highlightedElements;
        fixture.detectChanges();
        expect(comp.apollonEditor).not.toBeNull();
        const apollonModel = comp.apollonEditor!.model;
        const elements: UMLElement[] = apollonModel.elements;
        const highlightedElement = elements.find((el) => el.id === 'elementId1');
        const notHighlightedElement = elements.find((el) => el.id === 'elementId2');
        const relationship = apollonModel.relationships[0];
        expect(highlightedElement).not.toBeNull();
        expect(highlightedElement!.highlight).toBe('red');
        expect(notHighlightedElement).not.toBeNull();
        expect(notHighlightedElement!.highlight).toBeUndefined();
        expect(relationship).not.toBeNull();
        expect(relationship!.highlight).toBe('blue');
    });

    it('should update model', () => {
        const newModel = generateMockModel('newElement1', 'newElement2', 'newRelationship');
        const changes = { model: { currentValue: newModel } as SimpleChange };
        fixture.detectChanges();
        const apollonSpy = jest.spyOn(comp.apollonEditor!, 'model', 'set');
        comp.ngOnChanges(changes);
        expect(apollonSpy).toHaveBeenCalledWith(newModel);
    });

    it('should update highlighted elements', () => {
        const highlightedElements = new Map();
        highlightedElements.set('elementId2', 'green');
        const changes = { highlightedElements: { currentValue: highlightedElements } as SimpleChange };
        comp.umlModel = mockModel;
        fixture.detectChanges();
        comp.highlightedElements = highlightedElements;
        comp.ngOnChanges(changes);
        expect(comp.apollonEditor).not.toBeNull();
        const apollonModel = comp.apollonEditor!.model;
        const elements: UMLElement[] = apollonModel.elements;
        const highlightedElement = elements.find((el) => el.id === 'elementId2');
        const notHighlightedElement = elements.find((el) => el.id === 'elementId1');
        const relationship = apollonModel.relationships[0];
        expect(highlightedElement).not.toBeNull();
        expect(highlightedElement!.highlight).toBe('green');
        expect(notHighlightedElement).not.toBeNull();
        expect(notHighlightedElement!.highlight).toBeUndefined();
        expect(relationship).not.toBeNull();
        expect(relationship!.highlight).toBeUndefined();
    });

    it('should update highlighted assessments first round', () => {
        const changes = { highlightDifferences: { currentValue: true } as SimpleChange };
        comp.highlightDifferences = true;
        comp.umlModel = mockModel;
        fixture.detectChanges();
        comp.feedbacks = [mockFeedbackWithReference];
        comp.referencedFeedbacks = [mockFeedbackWithReference];
        jest.spyOn(translatePipe, 'transform').mockReturnValue('Second correction round');

        comp.ngOnChanges(changes);

        expect(comp.apollonEditor).not.toBeNull();
        const apollonModel = comp.apollonEditor!.model;
        const assessments: any = apollonModel.assessments;
        expect(assessments[0].labelColor).toEqual(comp.secondCorrectionRoundColor);
        expect(assessments[0].label).toBe('Second correction round');
        expect(assessments[0].score).toBe(30);
    });

    it('should update highlighted assessments', () => {
        const changes = { highlightDifferences: { currentValue: true } as SimpleChange };
        comp.highlightDifferences = true;
        comp.umlModel = mockModel;
        fixture.detectChanges();
        comp.feedbacks = [mockFeedbackWithReferenceCopied];
        comp.referencedFeedbacks = [mockFeedbackWithReferenceCopied];
        jest.spyOn(translatePipe, 'transform').mockReturnValue('First correction round');

        comp.ngOnChanges(changes);

        expect(comp.apollonEditor).not.toBeNull();
        const apollonModel = comp.apollonEditor!.model;
        const assessments: any = apollonModel.assessments;
        expect(assessments[0].labelColor).toEqual(comp.firstCorrectionRoundColor);
        expect(assessments[0].label).toBe('First correction round');
        expect(assessments[0].score).toBe(35);
    });

    it('should update feedbacks', () => {
        const newMockFeedbackWithReference = { text: 'NewFeedbackWithReference', referenceId: 'relationshipId', reference: 'reference', credits: 30 } as Feedback;
        const newMockFeedbackWithoutReference = { text: 'NewFeedbackWithoutReference', credits: 30, type: FeedbackType.MANUAL_UNREFERENCED } as Feedback;
        const newMockFeedbackInvalid = { text: 'NewFeedbackInvalid', referenceId: '4', reference: 'reference' };
        const newMockValidFeedbacks = [newMockFeedbackWithReference, newMockFeedbackWithoutReference];
        const newMockFeedbacks = [...newMockValidFeedbacks, newMockFeedbackInvalid];
        comp.umlModel = mockModel;
        comp.readOnly = true;
        fixture.detectChanges();
        const changes = { feedbacks: { currentValue: newMockFeedbacks } as SimpleChange };
        comp.ngOnChanges(changes);
        expect(comp.feedbacks).toEqual(newMockFeedbacks);
        expect(comp.referencedFeedbacks).toEqual([newMockFeedbackWithReference]);
    });
});
