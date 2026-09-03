import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// Hoisting prevents Apollon's React scheduler from starting in jsdom.
const { MockApollonEditor } = vi.hoisted(() => {
    const deepClone = (obj: any): any => (obj ? JSON.parse(JSON.stringify(obj)) : {});

    class MockApollonEditorClass {
        _model: any;
        _options: any;
        _subscriptions = new Map<number, (model: any) => void>();
        _assessmentSelectionSubscriptions = new Map<number, (selections: string[]) => void>();
        _selectionChangeSubscriptions = new Map<number, (selectedElementIds: string[]) => void>();
        _regionElements = new Map<string, HTMLElement>();
        _subscriptionCounter = 0;

        subscribeToModelChange = vi.fn((callback: (model: any) => void) => {
            const id = ++this._subscriptionCounter;
            this._subscriptions.set(id, callback);
            return id;
        });

        subscribeToAssessmentSelection = vi.fn((callback: (selections: string[]) => void) => {
            const id = ++this._subscriptionCounter;
            this._assessmentSelectionSubscriptions.set(id, callback);
            return id;
        });

        subscribeToSelectionChange = vi.fn((callback: (selectedElementIds: string[]) => void) => {
            const id = ++this._subscriptionCounter;
            this._selectionChangeSubscriptions.set(id, callback);
            return id;
        });

        unsubscribe = vi.fn((id: number) => {
            this._subscriptions.delete(id);
            this._assessmentSelectionSubscriptions.delete(id);
            this._selectionChangeSubscriptions.delete(id);
        });

        destroy = vi.fn();

        setElementHighlights = vi.fn();

        revealAssessment = vi.fn();

        setReadonly = vi.fn();

        getRegionElement = vi.fn((region: string) => {
            if (!this._regionElements.has(region)) {
                this._regionElements.set(region, document.createElement('div'));
            }
            return this._regionElements.get(region)!;
        });

        releaseRegionElement = vi.fn((region: string) => {
            this._regionElements.delete(region);
        });

        updateControl = vi.fn();

        fitView = vi.fn();

        addOrUpdateAssessment = vi.fn((assessment: any) => {
            if (this._model) {
                if (!this._model.assessments) {
                    this._model.assessments = {};
                }
                this._model.assessments[assessment.modelElementId] = assessment;
            }
        });

        nextRender = Promise.resolve();

        constructor(_container: HTMLElement, options?: { model?: any }) {
            this._options = options;
            this._model = options?.model ? deepClone(options.model) : {};
            if (!this._model.nodes) this._model.nodes = [];
            if (!this._model.edges) this._model.edges = [];
            if (!this._model.assessments) this._model.assessments = {};
        }

        get model() {
            return this._model;
        }

        set model(value: any) {
            this._model = value;
        }
    }

    return { MockApollonEditor: MockApollonEditorClass };
});

vi.mock('@tumaet/apollon', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@tumaet/apollon')>();
    return {
        ...actual,
        ApollonEditor: MockApollonEditor,
    };
});

import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { ApollonEditor, UMLDiagramType, UMLModel } from '@tumaet/apollon';
import {
    FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER,
    FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER,
    Feedback,
    FeedbackCorrectionErrorType,
    FeedbackType,
} from 'app/assessment/shared/entities/feedback.model';
import { ModelingAssessmentComponent } from 'app/modeling/manage/assess/modeling-assessment.component';
import { ModelingAssessmentTopLeftDirective } from 'app/modeling/manage/assess/modeling-assessment-top-left.directive';
import { ModelingExplanationEditorComponent } from 'app/modeling/shared/modeling-explanation-editor/modeling-explanation-editor.component';
import { MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ModelElementCount } from 'app/modeling/shared/entities/modeling-submission.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import testClassDiagram from 'test/helpers/sample/modeling/test-models/class-diagram.json';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { APOLLON_FULLSCREEN_FRAME_CLASS } from 'app/modeling/shared/fullscreen/fullscreen-presentation.service';

function createV4ModelWithNodes(): UMLModel {
    const v3Model = deepClone(testClassDiagram as any);
    const nodes: any[] = [];
    const edges: any[] = [];

    for (const [id, element] of Object.entries(v3Model.elements || {})) {
        nodes.push({ ...(element as any), id, data: {} });
    }
    for (const [id, rel] of Object.entries(v3Model.relationships || {})) {
        edges.push({ ...(rel as any), id, data: {} });
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

function findElementById(elements: any[], id: string): any {
    return elements.find((el: any) => el.id === id);
}

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
    let fixture: ComponentFixture<ModelingAssessmentComponent>;
    let comp: ModelingAssessmentComponent;
    let translatePipe: ArtemisTranslatePipe;

    const PACKAGE_ID = 'b234e5cb-33e3-4957-ae04-f7990ce8571a';
    const CONNECTED_CLASS_ID = '2f67120e-b491-4222-beb1-79e87c2cf54d';
    const RELATIONSHIP_ID = '5a9a4eb3-8281-4de4-b0f2-3e2f164574bd';
    const originalFullscreenEnabled = Object.getOwnPropertyDescriptor(document, 'fullscreenEnabled');

    const makeMockModel = () => deepClone(testClassDiagram as unknown as UMLModel);

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

    const waitForApollonInitialization = async () => {
        await fixture.whenStable();
        await (comp.apollonEditor as unknown as { nextRender: Promise<void> } | undefined)?.nextRender;
        await fixture.whenStable();
    };

    beforeEach(() => {
        Object.defineProperty(document, 'fullscreenEnabled', { configurable: true, value: true });
        TestBed.configureTestingModule({
            imports: [MockModule(FormsModule), ModelingAssessmentComponent, ModelingExplanationEditorComponent, MockPipe(ArtemisTranslatePipe)],
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
        if (comp) {
            comp.ngOnDestroy();
        }
        fixture?.destroy();
        if (originalFullscreenEnabled) {
            Object.defineProperty(document, 'fullscreenEnabled', originalFullscreenEnabled);
        } else {
            Reflect.deleteProperty(document, 'fullscreenEnabled');
        }
        vi.restoreAllMocks();
    });

    it('should scope the Artemis theme bridge to the Apollon host', () => {
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('.apollon-container.artemis-apollon-theme'))).not.toBeNull();
    });

    it('should display explanation editor if there is an explanation', () => {
        const explanation = 'Explanation';
        fixture.componentRef.setInput('explanation', explanation);
        fixture.detectChanges();
        const explanationEditor = fixture.debugElement.query(By.directive(ModelingExplanationEditorComponent));
        expect(explanationEditor).not.toBeNull();
        expect(explanationEditor.componentInstance.explanation()).toEqual(explanation);
        expect(explanationEditor.componentInstance.readOnly()).toBe(true);
        expect(explanationEditor.componentInstance.autosizeMaxRows()).toBe(6);
    });

    it('should mount an explanation that arrives after Apollon initialization', async () => {
        fixture.componentRef.setInput('umlModel', makeMockModel());
        fixture.detectChanges();

        const editor = comp.apollonEditor as unknown as InstanceType<typeof MockApollonEditor>;
        const bottomCenter = fixture.nativeElement.querySelector('.modeling-assessment__region--bottom-center') as HTMLElement;
        fixture.componentRef.setInput('explanation', 'Late explanation');
        fixture.detectChanges();
        await waitForApollonInitialization();

        expect(editor._regionElements.get('bottom-center')?.contains(bottomCenter)).toBe(true);
        expect(bottomCenter.querySelector('jhi-modeling-explanation-editor')).not.toBeNull();
    });

    it('initializes Apollon with translated labels and scroll lock disabled', () => {
        fixture.componentRef.setInput('umlModel', makeMockModel());
        fixture.componentRef.setInput('diagramType', UMLDiagramType.ClassDiagram);

        fixture.detectChanges();
        expect(comp.apollonEditor).not.toBeNull();
        const editor = comp.apollonEditor as unknown as InstanceType<typeof MockApollonEditor>;
        expect(editor._options.scrollLock).toBe(false);
        expect(editor._options.labels).toBeDefined();
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

        expect(comp.referencedFeedbacks).toEqual([mockFeedbackWithReference, mockFeedbackInvalid]);
        expect(comp.resultFeedbacks()).toEqual(mockFeedbacks);
    });

    it('should remove assessments and feedback mappings that disappear from the server result', async () => {
        fixture.componentRef.setInput('umlModel', makeMockModel());
        fixture.componentRef.setInput('resultFeedbacks', [mockFeedbackWithReference]);
        fixture.detectChanges();
        await waitForApollonInitialization();

        expect(comp.apollonEditor!.model.assessments[RELATIONSHIP_ID]).toBeDefined();
        expect(comp.elementFeedback.has(RELATIONSHIP_ID)).toBe(true);

        fixture.componentRef.setInput('resultFeedbacks', []);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(comp.apollonEditor!.model.assessments).toEqual({});
        expect(comp.elementFeedback.has(RELATIONSHIP_ID)).toBe(false);
    });

    it('should calculate drop info', () => {
        const mockModel = makeMockModel();
        fixture.componentRef.setInput('umlModel', mockModel);
        fixture.detectChanges();
        fixture.componentRef.setInput('resultFeedbacks', [mockFeedbackWithGradingInstruction]);
        fixture.detectChanges();

        const assessment = Object.values(comp.apollonEditor!.model.assessments)[0];
        expect(assessment.dropInfo).toBe(mockFeedbackWithGradingInstruction.gradingInstruction);
    });

    it('should update element counts', async () => {
        const mockModel = makeMockModel();
        const v4Model = createV4ModelWithNodes();

        const elementCounts: ModelElementCount[] = [
            { elementId: PACKAGE_ID, numberOfOtherElements: 5 },
            { elementId: CONNECTED_CLASS_ID, numberOfOtherElements: 3 },
        ];

        const spy = vi.spyOn(translatePipe, 'transform').mockImplementation((key: string | undefined | null, params?: object) => {
            const affectedSubmissionsCount = (params as { affectedSubmissionsCount?: number } | undefined)?.affectedSubmissionsCount;
            if (key === 'artemisApp.modelingAssessment.impactWarning' && affectedSubmissionsCount) {
                return `Warning: ${affectedSubmissionsCount} other submissions`;
            }
            return key ?? '';
        });

        fixture.componentRef.setInput('umlModel', mockModel);
        fixture.componentRef.setInput('elementCounts', elementCounts);
        fixture.detectChanges();

        await waitForApollonInitialization();

        expect(comp.apollonEditor).toBeDefined();

        const { getCapturedModel } = mockApollonEditorModel(comp.apollonEditor!, v4Model);

        await (comp as any).updateElementCounts(elementCounts);

        expect(spy).toHaveBeenCalledWith('artemisApp.modelingAssessment.impactWarning', { affectedSubmissionsCount: 5 });
        expect(spy).toHaveBeenCalledWith('artemisApp.modelingAssessment.impactWarning', { affectedSubmissionsCount: 3 });

        const updatedModel = getCapturedModel();
        expect(findElementById(updatedModel.nodes as any[], PACKAGE_ID).data.assessmentNote).toBe('Warning: 5 other submissions');
        expect(findElementById(updatedModel.nodes as any[], CONNECTED_CLASS_ID).data.assessmentNote).toBe('Warning: 3 other submissions');

        const otherNodeId = 'ccac14e5-c828-4afb-ab97-0fb2a67e77d6';
        expect(findElementById(updatedModel.nodes as any[], otherNodeId).data.assessmentNote).toBeUndefined();
    });

    it('should generate feedback from assessment', () => {
        const mockModel = makeMockModel();
        fixture.componentRef.setInput('umlModel', mockModel);
        fixture.componentRef.setInput('resultFeedbacks', [mockFeedbackWithGradingInstruction]);

        fixture.detectChanges();

        comp.generateFeedbackFromAssessment(Object.values(comp.apollonEditor!.model.assessments));
        expect(comp.elementFeedback.get(mockFeedbackWithGradingInstruction.referenceId!)).toEqual(mockFeedbackWithGradingInstruction);
    });

    describe('generateFeedbackFromAssessment', () => {
        const assessmentFor = (overrides: Record<string, unknown> = {}) =>
            ({ modelElementId: PACKAGE_ID, elementType: 'Package', score: 1, feedback: 'Looks right', ...overrides }) as any;

        it('creates feedback for an element that has none yet', () => {
            const [created] = comp.generateFeedbackFromAssessment([assessmentFor()]);

            expect(created.referenceId).toBe(PACKAGE_ID);
            expect(created.credits).toBe(1);
            expect(created.text).toBe('Looks right');
        });

        it('references an element by its UML type, not by the kind Apollon reports', () => {
            fixture.componentRef.setInput('umlModel', makeMockModel());

            const [created] = comp.generateFeedbackFromAssessment([assessmentFor({ elementType: 'node' })]);

            expect(created.referenceType).toBe('Package');
            expect(created.reference).toBe(`Package:${PACKAGE_ID}`);
        });

        it('falls back to what Apollon reports when the model no longer holds the element', () => {
            fixture.componentRef.setInput('umlModel', makeMockModel());

            const [created] = comp.generateFeedbackFromAssessment([assessmentFor({ modelElementId: 'no-longer-in-the-model', elementType: 'node' })]);

            expect(created.reference).toBe('node:no-longer-in-the-model');
        });

        it('drops the grading instruction when the tutor overrides its score', () => {
            const graded = Feedback.forModeling(1, 'Looks right', PACKAGE_ID, 'Package');
            graded.gradingInstruction = { id: 7 } as any;
            comp.elementFeedback.set(PACKAGE_ID, graded);

            comp.generateFeedbackFromAssessment([assessmentFor({ score: 2 })]);

            expect(graded.credits).toBe(2);
            expect(graded.gradingInstruction).toBeUndefined();
        });

        it('marks an accepted suggestion as adapted once its text is edited, and keeps the title unprefixed', () => {
            const suggestion = Feedback.forModeling(1, 'Original detail', PACKAGE_ID, 'Package');
            suggestion.text = FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER + 'Missing abstraction';
            comp.elementFeedback.set(PACKAGE_ID, suggestion);
            comp['shownInApollon'].set(PACKAGE_ID, 'Original detail');

            comp.generateFeedbackFromAssessment([assessmentFor({ feedback: 'Edited detail' })]);

            expect(suggestion.text).toBe(FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER + 'Missing abstraction');
            expect(suggestion.detailText).toBe('Edited detail');
        });

        it('leaves an already adapted suggestion titled once and still takes the newest detail', () => {
            const adapted = Feedback.forModeling(1, 'Original detail', PACKAGE_ID, 'Package');
            adapted.text = FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER + 'Missing abstraction';
            comp.elementFeedback.set(PACKAGE_ID, adapted);

            comp.generateFeedbackFromAssessment([assessmentFor({ feedback: 'Edited again' })]);

            expect(adapted.text).toBe(FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER + 'Missing abstraction');
            expect(adapted.detailText).toBe('Edited again');
        });

        it('marks an accepted suggestion as adapted when only its score changes', () => {
            const suggestion = Feedback.forModeling(1, 'Original detail', PACKAGE_ID, 'Package');
            suggestion.text = FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER + 'Missing abstraction';
            comp.elementFeedback.set(PACKAGE_ID, suggestion);
            comp['shownInApollon'].set(PACKAGE_ID, 'Original detail');

            comp.generateFeedbackFromAssessment([assessmentFor({ score: 2, feedback: 'Original detail' })]);

            expect(suggestion.text).toBe(FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER + 'Missing abstraction');
        });

        it('does not mark a non-suggestion feedback as adapted', () => {
            const manual = Feedback.forModeling(1, 'Instructor comment', PACKAGE_ID, 'Package');
            comp.elementFeedback.set(PACKAGE_ID, manual);

            comp.generateFeedbackFromAssessment([assessmentFor({ score: 2, feedback: 'Edited instructor comment' })]);

            expect(manual.text).toBe('Edited instructor comment');
        });

        it('attaches the grading instruction the tutor dropped on an element, and detaches it once removed', () => {
            const instruction = { id: 7 } as any;
            comp.generateFeedbackFromAssessment([assessmentFor({ dropInfo: instruction })]);
            expect(comp.elementFeedback.get(PACKAGE_ID)!.gradingInstruction).toBe(instruction);

            comp.generateFeedbackFromAssessment([assessmentFor({ dropInfo: undefined })]);
            expect(comp.elementFeedback.get(PACKAGE_ID)!.gradingInstruction).toBeUndefined();
        });

        it('forgets feedback for elements the tutor deleted from the diagram', () => {
            comp.elementFeedback.set(RELATIONSHIP_ID, Feedback.forModeling(1, 'Stale', RELATIONSHIP_ID, 'ClassUnidirectional'));

            comp.generateFeedbackFromAssessment([assessmentFor()]);

            expect(comp.elementFeedback.has(RELATIONSHIP_ID)).toBe(false);
        });
    });

    it('reveals one element for a feedback list, and clears the selection again', async () => {
        fixture.componentRef.setInput('umlModel', makeMockModel());
        fixture.detectChanges();
        await waitForApollonInitialization();
        const reveal = comp.apollonEditor!.revealAssessment as unknown as ReturnType<typeof vi.fn>;

        comp.revealAssessment(PACKAGE_ID);
        expect(reveal).toHaveBeenCalledWith(PACKAGE_ID);

        comp.revealAssessment(undefined);
        expect(reveal).toHaveBeenLastCalledWith(null);
    });

    it('applies highlight overlays to the editor when the highlightedElements input changes', async () => {
        fixture.componentRef.setInput('umlModel', makeMockModel());
        fixture.detectChanges();
        await waitForApollonInitialization();

        const setHighlights = comp.apollonEditor!.setElementHighlights as unknown as ReturnType<typeof vi.fn>;
        setHighlights.mockClear();

        const highlights = new Map<string, string>([
            [PACKAGE_ID, 'red'],
            [RELATIONSHIP_ID, 'blue'],
        ]);
        fixture.componentRef.setInput('highlightedElements', highlights);
        fixture.detectChanges();
        await waitForApollonInitialization();

        expect(setHighlights).toHaveBeenCalledWith(highlights);
    });

    it('clears stale overlays when highlightedElements is reset to undefined (no lingering highlights)', async () => {
        fixture.componentRef.setInput('umlModel', makeMockModel());
        fixture.componentRef.setInput('highlightedElements', new Map<string, string>([[PACKAGE_ID, 'red']]));
        fixture.detectChanges();
        await waitForApollonInitialization();

        const setHighlights = comp.apollonEditor!.setElementHighlights as unknown as ReturnType<typeof vi.fn>;
        setHighlights.mockClear();

        fixture.componentRef.setInput('highlightedElements', undefined);
        fixture.detectChanges();
        await waitForApollonInitialization();

        expect(setHighlights).toHaveBeenCalledWith(null);
    });

    it('applies a replacement input model to the mounted editor', async () => {
        const initialModel = makeMockModel();
        fixture.componentRef.setInput('umlModel', initialModel);
        fixture.detectChanges();
        await waitForApollonInitialization();
        expect(comp.apollonEditor).not.toBeNull();

        const newModel = makeMockModel();
        newModel.title = 'Replacement model';
        fixture.componentRef.setInput('umlModel', newModel);
        fixture.detectChanges();
        await waitForApollonInitialization();

        const apollonModel = comp.apollonEditor!.model;
        expect(apollonModel.title).toBe('Replacement model');
    });

    it('should update highlighted assessments first round', async () => {
        fixture.componentRef.setInput('highlightDifferences', true);
        fixture.componentRef.setInput('umlModel', makeMockModel());
        fixture.componentRef.setInput('resultFeedbacks', [mockFeedbackWithReference]);
        comp.referencedFeedbacks = [mockFeedbackWithReference];
        vi.spyOn(translatePipe, 'transform').mockReturnValue('Second correction round');

        fixture.detectChanges();
        await waitForApollonInitialization();

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

        await waitForApollonInitialization();

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
    it('should lock the live canvas when readOnly flips after submitting', async () => {
        fixture.componentRef.setInput('readOnly', false);
        fixture.detectChanges();
        await fixture.whenStable();

        const editor = comp['apollonEditor'] as unknown as InstanceType<typeof MockApollonEditor>;
        expect(editor.setReadonly).not.toHaveBeenCalledWith(true);

        fixture.componentRef.setInput('readOnly', true);
        fixture.detectChanges();

        expect(editor.setReadonly).toHaveBeenCalledWith(true);
        expect(editor.subscribeToSelectionChange).toHaveBeenCalled();

        fixture.componentRef.setInput('readOnly', false);
        fixture.detectChanges();

        expect(editor.setReadonly).toHaveBeenLastCalledWith(false);
        expect(editor.unsubscribe).toHaveBeenCalled();
    });

    it('should report the ids of the selected elements, not of their assessments', async () => {
        fixture.componentRef.setInput('readOnly', true);
        fixture.detectChanges();
        await fixture.whenStable();

        const editor = comp['apollonEditor'] as unknown as InstanceType<typeof MockApollonEditor>;
        const emitted: string[][] = [];
        comp.selectedElementIdsChanged.subscribe((ids: string[]) => emitted.push(ids));

        expect(editor.subscribeToSelectionChange).toHaveBeenCalledOnce();
        expect(editor.subscribeToAssessmentSelection).not.toHaveBeenCalled();

        for (const callback of editor._selectionChangeSubscriptions.values()) {
            callback([PACKAGE_ID]);
        }

        expect(emitted).toEqual([[PACKAGE_ID]]);
    });
    const stubFullscreenApi = (requestFullscreen: () => Promise<void>) => {
        let fullscreenElement: Element | null = null;
        const originals = {
            fullscreenElement: Object.getOwnPropertyDescriptor(document, 'fullscreenElement'),
            exitFullscreen: Object.getOwnPropertyDescriptor(document, 'exitFullscreen'),
            requestFullscreen: Object.getOwnPropertyDescriptor(document.documentElement, 'requestFullscreen'),
        };
        const exitFullscreen = vi.fn(async () => {
            fullscreenElement = null;
        });
        Object.defineProperty(document.documentElement, 'requestFullscreen', {
            configurable: true,
            value: vi.fn(async () => {
                await requestFullscreen();
                fullscreenElement = document.documentElement;
            }),
        });
        Object.defineProperty(document, 'fullscreenElement', {
            configurable: true,
            get: () => fullscreenElement,
            set: (value) => {
                fullscreenElement = value;
            },
        });
        Object.defineProperty(document, 'exitFullscreen', { configurable: true, value: exitFullscreen });
        return {
            exitFullscreen,
            setFullscreenElement: (element: Element | null) => (fullscreenElement = element),
            restore: () => {
                for (const [key, descriptor] of [
                    ['fullscreenElement', originals.fullscreenElement],
                    ['exitFullscreen', originals.exitFullscreen],
                ] as const) {
                    if (descriptor) {
                        Object.defineProperty(document, key, descriptor);
                    } else {
                        Reflect.deleteProperty(document, key);
                    }
                }
                if (originals.requestFullscreen) {
                    Object.defineProperty(document.documentElement, 'requestFullscreen', originals.requestFullscreen);
                } else {
                    Reflect.deleteProperty(document.documentElement, 'requestFullscreen');
                }
            },
        };
    };

    it('should leave the page alone when something else already holds fullscreen', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        const api = stubFullscreenApi(async () => {});
        const frame = fixture.nativeElement.querySelector('.modeling-assessment') as HTMLElement;
        const originalParent = frame.parentNode;
        api.setFullscreenElement(document.createElement('video'));

        try {
            await comp.toggleFullscreen();

            expect(document.documentElement.requestFullscreen).not.toHaveBeenCalled();
            expect(frame.parentNode).toBe(originalParent);
            expect(comp.fullscreenActive()).toBe(false);
        } finally {
            api.restore();
        }
    });

    it('should put the frame back when the browser refuses the fullscreen request', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        const api = stubFullscreenApi(async () => {
            throw new Error('denied');
        });
        const frame = fixture.nativeElement.querySelector('.modeling-assessment') as HTMLElement;
        const originalParent = frame.parentNode;

        try {
            await comp.toggleFullscreen();

            expect(frame.parentNode).toBe(originalParent);
            expect(frame.classList.contains(APOLLON_FULLSCREEN_FRAME_CLASS)).toBe(false);
            expect(comp.fullscreenActive()).toBe(false);
        } finally {
            api.restore();
        }
    });

    it('should exit fullscreen when destroyed while it owns the screen', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        const api = stubFullscreenApi(async () => {});

        try {
            await comp.toggleFullscreen();
            comp['onFullscreenChange']();
            expect(comp.fullscreenActive()).toBe(true);

            comp.ngOnDestroy();

            expect(api.exitFullscreen).toHaveBeenCalledOnce();
            expect(comp.fullscreenActive()).toBe(false);
        } finally {
            api.restore();
        }
    });

    it('should take the document root fullscreen with its frame promoted to the body', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        const api = stubFullscreenApi(async () => {});
        const frame = fixture.nativeElement.querySelector('.modeling-assessment') as HTMLElement;
        const originalParent = frame.parentNode;

        try {
            await comp.toggleFullscreen();
            comp['onFullscreenChange']();
            fixture.detectChanges();

            expect(document.documentElement.requestFullscreen).toHaveBeenCalledOnce();
            expect(comp.fullscreenActive()).toBe(true);
            expect(frame.parentNode).toBe(document.body);
            expect(frame.classList.contains(APOLLON_FULLSCREEN_FRAME_CLASS)).toBe(true);

            await comp.toggleFullscreen();
            comp['onFullscreenChange']();
            fixture.detectChanges();

            expect(api.exitFullscreen).toHaveBeenCalledOnce();
            expect(comp.fullscreenActive()).toBe(false);
            expect(frame.parentNode).toBe(originalParent);
            expect(frame.classList.contains(APOLLON_FULLSCREEN_FRAME_CLASS)).toBe(false);
        } finally {
            comp['restoreFullscreenPresentation']();
            api.restore();
        }
    });
});
@Component({ selector: 'jhi-chrome-notice-stub', template: '<span class="chrome-notice">notice</span>' })
class ChromeNoticeStubComponent {}
@Component({
    selector: 'jhi-modeling-assessment-bare-slot-host',
    template: `
        <jhi-modeling-assessment [umlModel]="umlModel" [diagramType]="diagramType">
            <jhi-chrome-notice-stub modelingAssessmentTopLeft data-testid="bare-notice" />
        </jhi-modeling-assessment>
    `,
    imports: [ModelingAssessmentComponent, ModelingAssessmentTopLeftDirective, ChromeNoticeStubComponent],
})
class ModelingAssessmentBareSlotHostComponent {
    readonly umlModel = createV4ModelWithNodes();
    readonly diagramType = UMLDiagramType.ClassDiagram;
}

@Component({
    selector: 'jhi-modeling-assessment-chrome-host',
    template: `
        <jhi-modeling-assessment [umlModel]="umlModel" [diagramType]="diagramType">
            <jhi-chrome-notice-stub [modelingAssessmentTopLeft]="showNotice()" data-testid="chrome-notice" />
        </jhi-modeling-assessment>
    `,
    imports: [ModelingAssessmentComponent, ModelingAssessmentTopLeftDirective, ChromeNoticeStubComponent],
})
class ModelingAssessmentChromeHostComponent {
    readonly showNotice = signal(false);
    readonly umlModel = createV4ModelWithNodes();
    readonly diagramType = UMLDiagramType.ClassDiagram;
}
describe('ModelingAssessmentComponent chrome regions', () => {
    let fixture: ComponentFixture<ModelingAssessmentChromeHostComponent>;
    let editor: InstanceType<typeof MockApollonEditor>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [ModelingAssessmentChromeHostComponent, ModelingAssessmentBareSlotHostComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(ArtemisTranslatePipe), { provide: TranslateService, useClass: MockTranslateService }],
        });

        fixture = TestBed.createComponent(ModelingAssessmentChromeHostComponent);
        fixture.detectChanges();
        await fixture.whenStable();
        editor = fixture.debugElement.query(By.directive(ModelingAssessmentComponent)).componentInstance.apollonEditor as unknown as InstanceType<typeof MockApollonEditor>;
    });

    afterEach(() => {
        fixture?.destroy();
        vi.restoreAllMocks();
    });

    const noticeElement = () => fixture.debugElement.query(By.css('[data-testid="chrome-notice"]')).nativeElement as HTMLElement;

    it('should never mount an unoccupied region, even though the slot is filled', () => {
        expect(fixture.debugElement.query(By.css('[data-testid="chrome-notice"]'))).not.toBeNull();
        expect(editor.getRegionElement).not.toHaveBeenCalledWith('top-left');
        expect(fixture.debugElement.query(By.css('.modeling-assessment__region--top-left')).nativeElement.classList).not.toContain('modeling-assessment__region--mounted');
    });

    it('should mount the projected island into the top-left region and release it again', async () => {
        fixture.componentInstance.showNotice.set(true);
        fixture.detectChanges();
        await fixture.whenStable();

        const region = editor._regionElements.get('top-left');
        expect(region).toBeDefined();
        expect(region!.contains(noticeElement())).toBe(true);
        expect(noticeElement().closest('.modeling-assessment__region--top-left')?.classList).toContain('modeling-assessment__region--mounted');

        fixture.componentInstance.showNotice.set(false);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(editor.releaseRegionElement).toHaveBeenCalledWith('top-left');
        expect(editor._regionElements.has('top-left')).toBe(false);
    });

    it('should treat a bare marker attribute as permanently occupied', async () => {
        const bare = TestBed.createComponent(ModelingAssessmentBareSlotHostComponent);
        bare.detectChanges();
        await bare.whenStable();

        const bareEditor = bare.debugElement.query(By.directive(ModelingAssessmentComponent)).componentInstance.apollonEditor as unknown as InstanceType<typeof MockApollonEditor>;
        expect(bareEditor._regionElements.get('top-left')!.contains(bare.debugElement.query(By.css('[data-testid="bare-notice"]')).nativeElement)).toBe(true);
        bare.destroy();
    });
    it('should reserve room for the panel on every change but frame the camera only once', () => {
        const component = fixture.debugElement.query(By.directive(ModelingAssessmentComponent)).componentInstance;
        let panelWidth = 0;
        const disclosure = fixture.debugElement.query(By.css('jhi-apollon-rail-disclosure')).componentInstance;
        vi.spyOn(disclosure, 'getVisiblePanelRect').mockImplementation(() => ({ width: panelWidth }) as DOMRect);

        const scheduleFitView = vi.spyOn(component as any, 'scheduleFitView');
        const reserve = () => (component as any).reserveRoomForPanel();
        editor.updateControl.mockClear();
        (component as any).hasFramedForPanelInset = false;
        (component as any).lastReservedPanelWidth = -1;

        panelWidth = 320;
        reserve();
        panelWidth = 0; // collapsed
        reserve();
        panelWidth = 280; // reopened at a different width
        reserve();

        expect(editor.updateControl).toHaveBeenCalledTimes(3);
        expect(editor.updateControl).toHaveBeenLastCalledWith('apollon:host:right-rail', expect.objectContaining({ inset: { right: 280 } }));

        expect(scheduleFitView).toHaveBeenCalledTimes(1);
    });

    it('should observe and avoid the floating panel rather than its rail host', () => {
        const component = fixture.debugElement.query(By.directive(ModelingAssessmentComponent)).componentInstance;
        const disclosure = fixture.debugElement.query(By.css('jhi-apollon-rail-disclosure'));
        const floatingPanel = disclosure.query(By.css('.apollon-rail-disclosure__panel')).nativeElement as HTMLElement;
        const panelRect = { left: 500, right: 900, width: 400 } as DOMRect;
        vi.spyOn(floatingPanel, 'getBoundingClientRect').mockReturnValue(panelRect);
        const observe = vi.spyOn(ResizeObserver.prototype, 'observe');

        (component as any).observePanelWidth();

        expect(observe).toHaveBeenCalledWith(floatingPanel);
        expect((component as any).panelObstruction()).toBe(panelRect);

        disclosure.componentInstance.visible.set(false);
        expect((component as any).panelObstruction()).toBeUndefined();
    });
});
