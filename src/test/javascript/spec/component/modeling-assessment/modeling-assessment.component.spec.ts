import { SimpleChange } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { ApollonEditor, UMLDiagramType, UMLElement, UMLModel, UMLRelationship } from '@ls1intum/apollon';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { ModelingAssessmentComponent } from 'app/exercises/modeling/assess/modeling-assessment.component';
import { ModelingExplanationEditorComponent } from 'app/exercises/modeling/shared/modeling-explanation-editor.component';
import { ScoreDisplayComponent } from 'app/shared/score-display/score-display.component';
import * as chai from 'chai';
import { MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import * as sinon from 'sinon';
import sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

chai.use(sinonChai);
const expect = chai.expect;

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
    const mockFeedbackWithReference = { text: 'FeedbackWithReference', referenceId: 'relationshipId', reference: 'reference', credits: 30 } as Feedback;
    const mockFeedbackWithReferenceCopied = {
        text: 'FeedbackWithReference Copied',
        referenceId: 'relationshipId',
        reference: 'reference',
        credits: 35,
        copiedFeedbackId: 12,
    } as Feedback;
    const mockFeedbackWithoutReference = { text: 'FeedbackWithoutReference', credits: 30, type: FeedbackType.MANUAL_UNREFERENCED } as Feedback;
    const mockFeedbackInvalid = { text: 'FeedbackInvalid', referenceId: '4', reference: 'reference' };
    const mockValidFeedbacks = [mockFeedbackWithReference, mockFeedbackWithoutReference];
    const mockFeedbacks = [...mockValidFeedbacks, mockFeedbackInvalid];

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

    afterEach(function () {
        sinon.restore();
    });

    it('should show  title if any', () => {
        const title = 'Test Title';
        comp.title = title;
        fixture.detectChanges();
        const el = fixture.debugElement.query((de) => de.nativeElement.textContent === title);
        expect(el).to.exist;
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
            expect(scoreDisplay).to.exist;
            expect(scoreDisplay.componentInstance.score).to.equal(totalScore);
            expect(scoreDisplay.componentInstance.maxPoints).to.equal(maxScore);
        });

        it('should not display score if displayPoints wrong', () => {
            comp.displayPoints = false;
            fixture.detectChanges();
            const scoreDisplay = fixture.debugElement.query(By.directive(ScoreDisplayComponent));
            expect(scoreDisplay).to.not.exist;
        });
    });

    it('should display explanation editor if there is an explanation', () => {
        const explanation = 'Explanation';
        comp.explanation = explanation;
        fixture.detectChanges();
        const explanationEditor = fixture.debugElement.query(By.directive(ModelingExplanationEditorComponent));
        expect(explanationEditor).to.exist;
        expect(explanationEditor.componentInstance.explanation).to.equal(explanation);
        expect(explanationEditor.componentInstance.readOnly).to.equal(true);
    });

    it('should initialize apollon editor', () => {
        comp.umlModel = mockModel;
        comp.diagramType = UMLDiagramType.ClassDiagram;
        fixture.detectChanges();
        expect(comp.apollonEditor).to.exist;
    });

    it('should filter references', () => {
        comp.umlModel = mockModel;
        comp.readOnly = true;
        comp.feedbacks = mockFeedbacks;
        fixture.detectChanges();
        expect(comp.referencedFeedbacks).to.deep.equal([mockFeedbackWithReference]);
        expect(comp.unreferencedFeedbacks).to.deep.equal([mockFeedbackWithoutReference]);
        expect(comp.feedbacks).to.deep.equal(mockFeedbacks);
    });

    it('should highlight elements', () => {
        const highlightedElements = new Map();
        highlightedElements.set('elementId1', 'red');
        highlightedElements.set('relationshipId', 'blue');
        comp.umlModel = mockModel;
        comp.highlightedElements = highlightedElements;
        fixture.detectChanges();
        expect(comp.apollonEditor).to.exist;
        const apollonModel = comp.apollonEditor!.model;
        const elements: UMLElement[] = apollonModel.elements;
        const highlightedElement = elements.find((el) => el.id === 'elementId1');
        const notHighlightedElement = elements.find((el) => el.id === 'elementId2');
        const relationship = apollonModel.relationships[0];
        expect(highlightedElement).to.exist;
        expect(highlightedElement!.highlight).to.equal('red');
        expect(notHighlightedElement).to.exist;
        expect(notHighlightedElement!.highlight).to.not.exist;
        expect(relationship).to.exist;
        expect(relationship!.highlight).to.equal('blue');
    });

    it('should update model', () => {
        const newModel = generateMockModel('newElement1', 'newElement2', 'newRelationship');
        const changes = { model: { currentValue: newModel } as SimpleChange };
        fixture.detectChanges();
        const apollonSpy = sinon.spy(comp.apollonEditor as ApollonEditor, 'model', ['set']);
        comp.ngOnChanges(changes);
        expect(apollonSpy.set).to.have.been.calledWithExactly(newModel);
    });

    it('should update highlighted elements', () => {
        const highlightedElements = new Map();
        highlightedElements.set('elementId2', 'green');
        const changes = { highlightedElements: { currentValue: highlightedElements } as SimpleChange };
        comp.umlModel = mockModel;
        fixture.detectChanges();
        comp.highlightedElements = highlightedElements;
        comp.ngOnChanges(changes);
        expect(comp.apollonEditor).to.exist;
        const apollonModel = comp.apollonEditor!.model;
        const elements: UMLElement[] = apollonModel.elements;
        const highlightedElement = elements.find((el) => el.id === 'elementId2');
        const notHighlightedElement = elements.find((el) => el.id === 'elementId1');
        const relationship = apollonModel.relationships[0];
        expect(highlightedElement).to.exist;
        expect(highlightedElement!.highlight).to.equal('green');
        expect(notHighlightedElement).to.exist;
        expect(notHighlightedElement!.highlight).to.not.exist;
        expect(relationship).to.exist;
        expect(relationship!.highlight).to.not.exist;
    });

    it('should update highlighted assessments first round', () => {
        const changes = { highlightDifferences: { currentValue: true } as SimpleChange };
        comp.highlightDifferences = true;
        comp.umlModel = mockModel;
        fixture.detectChanges();
        comp.feedbacks = [mockFeedbackWithReference];
        comp.referencedFeedbacks = [mockFeedbackWithReference];
        sinon.stub(translatePipe, 'transform').returns('Second correction round');

        comp.ngOnChanges(changes);

        expect(comp.apollonEditor).to.exist;
        const apollonModel = comp.apollonEditor!.model;
        const assessments: any = apollonModel.assessments;
        expect(assessments[0].labelColor).to.equal(comp.secondCorrectionRoundColor);
        expect(assessments[0].label).to.equal('Second correction round');
        expect(assessments[0].score).to.equal(30);
    });

    it('should update highlighted assessments', () => {
        const changes = { highlightDifferences: { currentValue: true } as SimpleChange };
        comp.highlightDifferences = true;
        comp.umlModel = mockModel;
        fixture.detectChanges();
        comp.feedbacks = [mockFeedbackWithReferenceCopied];
        comp.referencedFeedbacks = [mockFeedbackWithReferenceCopied];
        sinon.stub(translatePipe, 'transform').returns('First correction round');

        comp.ngOnChanges(changes);

        expect(comp.apollonEditor).to.exist;
        const apollonModel = comp.apollonEditor!.model;
        const assessments: any = apollonModel.assessments;
        expect(assessments[0].labelColor).to.equal(comp.firstCorrectionRoundColor);
        expect(assessments[0].label).to.equal('First correction round');
        expect(assessments[0].score).to.equal(35);
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
        expect(comp.feedbacks).to.deep.equal(newMockFeedbacks);
        expect(comp.referencedFeedbacks).to.deep.equal([newMockFeedbackWithReference]);
    });
});
