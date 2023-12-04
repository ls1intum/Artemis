import { SimpleChange } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { UMLDiagramType, UMLModel, UMLRelationship } from '@ls1intum/apollon';
import { Feedback, FeedbackCorrectionErrorType, FeedbackType } from 'app/entities/feedback.model';
import { ModelingAssessmentComponent } from 'app/exercises/modeling/assess/modeling-assessment.component';
import { ModelingExplanationEditorComponent } from 'app/exercises/modeling/shared/modeling-explanation-editor.component';
import { ScoreDisplayComponent } from 'app/shared/score-display/score-display.component';
import { MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ModelElementCount } from 'app/entities/modeling-submission.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { Text } from '@ls1intum/apollon/lib/es5/utils/svg/text';

describe('ModelingAssessmentComponent', () => {
    let fixture: ComponentFixture<ModelingAssessmentComponent>;
    let comp: ModelingAssessmentComponent;
    let translatePipe: ArtemisTranslatePipe;

    const generateMockModel = (elementId: string, elementId2: string, relationshipId: string) => {
        return {
            version: '3.0.0',
            type: 'ClassDiagram',
            size: { width: 740, height: 660 },
            interactive: { elements: {}, relationships: {} },

            elements: {
                [elementId]: {
                    id: elementId,
                    name: 'Package',
                    type: 'Package',
                    owner: null,
                    bounds: { x: 160, y: 40, width: 200, height: 100 },
                    highlight: undefined,
                },
                [elementId2]: {
                    id: elementId2,
                    name: 'Class',
                    type: 'Class',
                    owner: null,
                    bounds: { x: 280, y: 320, width: 200, height: 100 },
                    attributes: [],
                    methods: [],
                    highlight: undefined,
                },
            },
            relationships: {
                [relationshipId]: {
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
            },
            assessments: {},
        } as UMLModel;
    };

    // has to be overridden, because jsdom does not provide a getBBox() function for SVGTextElements
    Text.size = () => {
        return { width: 0, height: 0 };
    };

    const makeMockModel = () => generateMockModel('elementId1', 'elementId2', 'relationshipId');
    const mockFeedbackWithReference: Feedback = {
        text: 'FeedbackWithReference',
        referenceId: 'relationshipId',
        reference: 'reference',
        credits: 30,
        correctionStatus: 'CORRECT',
    };
    const mockFeedbackWithReferenceCopied: Feedback = {
        text: 'FeedbackWithReference Copied',
        referenceId: 'relationshipId',
        reference: 'reference',
        credits: 35,
        copiedFeedbackId: 12,
    };
    const mockFeedbackWithoutReference: Feedback = { text: 'FeedbackWithoutReference', credits: 30, type: FeedbackType.MANUAL_UNREFERENCED };
    const mockFeedbackInvalid: Feedback = { text: 'FeedbackInvalid', referenceId: '4', reference: 'reference', correctionStatus: FeedbackCorrectionErrorType.INCORRECT_SCORE };
    const mockValidFeedbacks = [mockFeedbackWithReference, mockFeedbackWithoutReference];
    const mockFeedbacks = [...mockValidFeedbacks, mockFeedbackInvalid];

    const mockFeedbackWithGradingInstruction: Feedback = {
        text: 'FeedbackWithGradingInstruction',
        referenceId: 'relationshipId',
        reference: 'reference',
        credits: 30,
        gradingInstruction: new GradingInstruction(),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(FormsModule)],
            declarations: [ModelingAssessmentComponent, ScoreDisplayComponent, ModelingExplanationEditorComponent, MockPipe(ArtemisTranslatePipe)],
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

        expect(spy).toHaveBeenCalledWith('artemisApp.assessment.messages.removeAssessmentInstructionLink');
        expect(spy).toHaveBeenCalledWith('artemisApp.exercise.assessmentInstruction');
        expect(spy).toHaveBeenCalledWith('artemisApp.assessment.feedbackHint');
        expect(Object.values(mockModel.assessments)[0].dropInfo.instruction).toBe(mockFeedbackWithGradingInstruction.gradingInstruction);

        // toHaveBeenCalledTimes(5): 2 from calculateLabel() + 3 from calculateDropInfo()
        expect(spy).toHaveBeenCalledTimes(5);
    });

    it('should update element counts', async () => {
        function getElementCounts(model: UMLModel): ModelElementCount[] {
            // Not sure whether this is the correct logic to build OtherModelElementCounts.
            return Object.values(model.elements).map((el) => ({
                elementId: el.id,
                numberOfOtherElements: Object.values(model.elements).length - 1,
            }));
        }

        const mockModel = makeMockModel();
        comp.umlModel = mockModel;
        const elementCounts = getElementCounts(mockModel);
        comp.elementCounts = elementCounts;
        const spy = jest.spyOn(translatePipe, 'transform');
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
        await comp.ngAfterViewInit();
        expect(comp.apollonEditor).not.toBeNull();

        const apollonModel = comp.apollonEditor!.model;
        const elements = apollonModel.elements;
        const highlightedElement = elements!['elementId1'];
        const notHighlightedElement = elements!['elementId2'];
        const relationship = (Object.values(apollonModel!.relationships) as UMLRelationship[])[0];
        expect(highlightedElement).not.toBeNull();
        expect(highlightedElement!.highlight).toBe('red');
        expect(notHighlightedElement).not.toBeNull();
        expect(notHighlightedElement!.highlight).toBeUndefined();
        expect(relationship).not.toBeNull();
        expect(relationship!.highlight).toBe('blue');
    });

    it('should update model', async () => {
        const newModel = generateMockModel('newElement1', 'newElement2', 'newRelationship');
        const changes = { model: { currentValue: newModel } as SimpleChange };
        fixture.detectChanges();
        const apollonSpy = jest.spyOn(comp.apollonEditor!, 'model', 'set');
        await fixture.whenStable();
        await comp.apollonEditor!.nextRender;
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
        await comp.ngOnChanges(changes);
        await comp.ngAfterViewInit();

        expect(comp.apollonEditor).not.toBeNull();
        const apollonModel = comp.apollonEditor!.model;
        const elements = apollonModel!.elements;
        const highlightedElement = elements!['elementId2'];
        const notHighlightedElement = elements!['elementId1'];
        const relationship = (Object.values(apollonModel!.relationships) as UMLRelationship[])[0];

        expect(highlightedElement).not.toBeNull();
        expect(highlightedElement!.highlight).toBe('green');
        expect(notHighlightedElement).not.toBeNull();
        expect(notHighlightedElement!.highlight).toBeUndefined();
        expect(relationship).not.toBeNull();
        expect(relationship!.highlight).toBeUndefined();
        expect(relationship!.highlight).toBeUndefined();
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

        await comp.apollonEditor!.nextRender;

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
        await comp.apollonEditor!.nextRender;

        expect(comp.apollonEditor).not.toBeNull();

        const apollonModel = comp.apollonEditor!.model;
        const assessments: any = Object.values(apollonModel.assessments);
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
        comp.umlModel = makeMockModel();
        comp.readOnly = true;
        fixture.detectChanges();
        const changes = { feedbacks: { currentValue: newMockFeedbacks } as SimpleChange };
        comp.ngOnChanges(changes);
        expect(comp.feedbacks).toEqual(newMockFeedbacks);
        expect(comp.referencedFeedbacks).toEqual([newMockFeedbackWithReference]);
    });
});
