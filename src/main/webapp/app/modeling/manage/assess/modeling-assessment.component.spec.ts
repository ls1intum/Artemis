import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// Hoisting prevents Apollon's React scheduler from starting in jsdom.
const { MockApollonEditor } = vi.hoisted(() => {
    const deepClone = (obj: any): any => (obj ? JSON.parse(JSON.stringify(obj)) : {});

    class MockApollonEditorClass {
        _model: any;
        _options: any;
        _subscriptions = new Map<number, (model: any) => void>();
        _assessmentSelectionSubscriptions = new Map<number, (selections: string[]) => void>();
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

        unsubscribe = vi.fn((id: number) => {
            this._subscriptions.delete(id);
            this._assessmentSelectionSubscriptions.delete(id);
        });

        destroy = vi.fn();

        setElementHighlights = vi.fn();

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
import { Feedback, FeedbackCorrectionErrorType, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
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
});

/** Stands in for a chrome island such as the Athena notice. */
@Component({ selector: 'jhi-chrome-notice-stub', template: '<span class="chrome-notice">notice</span>' })
class ChromeNoticeStubComponent {}

/** Host for the bare-attribute form of the marker, which must keep meaning "always occupied". */
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

/** Pins both halves of the {@link ModelingAssessmentRegion} contract: unoccupied reserves nothing, occupied really mounts. */
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
        // The slot is projected the whole time; only occupancy is off.
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
        // Containment, not just directive resolution: an unattached slot would mount an empty region.
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

    /**
     * The reserved inset is layout and tracks the panel; the camera belongs to the reader. Refitting
     * on every reservation threw away the tutor's zoom and pan each time they toggled "Your
     * assessment" — mid-assessment, while reading a single element.
     */
    it('should reserve room for the panel on every change but frame the camera only once', () => {
        const component = fixture.debugElement.query(By.directive(ModelingAssessmentComponent)).componentInstance;
        const panel = document.createElement('div');
        // `reserveRoomForPanel` measures the open panel, and skips when the width is unchanged.
        let panelWidth = 0;
        const openPanel = document.createElement('div');
        openPanel.className = 'apollon-rail-disclosure__panel';
        openPanel.getBoundingClientRect = () => ({ width: panelWidth }) as DOMRect;
        panel.appendChild(openPanel);

        const scheduleFitView = vi.spyOn(component as any, 'scheduleFitView');
        const reserve = () => (component as any).reserveRoomForPanel(panel);

        panelWidth = 320;
        reserve();
        panelWidth = 0; // collapsed
        reserve();
        panelWidth = 280; // reopened at a different width
        reserve();

        // The inset follows the panel every time, so a fit the reader asks for still clears it.
        expect(editor.updateControl).toHaveBeenCalledTimes(3);
        expect(editor.updateControl).toHaveBeenLastCalledWith('apollon:host:right-rail', expect.objectContaining({ inset: { right: 280 } }));

        // The camera is framed once and then left alone.
        expect(scheduleFitView).toHaveBeenCalledTimes(1);
    });
});
