import { AfterViewInit, Component, ElementRef, OnDestroy, computed, contentChild, effect, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import { ApollonEditor, ApollonMode, Assessment, UMLDiagramType, UMLModel } from '@tumaet/apollon';
import { captureException } from '@sentry/angular';
import {
    FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER,
    FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER,
    FEEDBACK_SUGGESTION_IDENTIFIER,
    Feedback,
    FeedbackType,
} from 'app/assessment/shared/entities/feedback.model';
import { ModelElementCount } from 'app/modeling/shared/entities/modeling-submission.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { ModelingComponent } from 'app/modeling/shared/modeling/modeling.component';
import { filterInvalidFeedback } from 'app/modeling/manage/assess/modeling-assessment.util';
import { faCommentDots } from '@fortawesome/free-solid-svg-icons';
import { ModelingExplanationEditorComponent } from 'app/modeling/shared/modeling-explanation-editor/modeling-explanation-editor.component';
import { normalizeApollonModel } from 'app/modeling/shared/apollon-model.util';
import { TranslateService } from '@ngx-translate/core';
import { createApollonLabels } from 'app/modeling/shared/modeling-editor/apollon-labels';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApollonRailDisclosureComponent } from 'app/modeling/shared/modeling-editor/apollon-rail-disclosure/apollon-rail-disclosure.component';
import {
    RAIL_DISCLOSURE_MAX_HEIGHT,
    applyBottomCenterPlacement,
    calculateBottomCenterPlacement,
    clearBottomCenterPlacement,
    measureRailDisclosureMaxHeight,
    synchronizeResizeObserverTargets,
} from 'app/modeling/shared/modeling-editor/apollon-chrome-placement';
import { isOccupied } from 'app/modeling/manage/assess/modeling-assessment-projection';
import { ModelingAssessmentPanelDirective } from 'app/modeling/manage/assess/modeling-assessment-panel.directive';
import { ModelingAssessmentTopLeftDirective } from 'app/modeling/manage/assess/modeling-assessment-top-left.directive';
import { ModelingAssessmentTopRightDirective } from 'app/modeling/manage/assess/modeling-assessment-top-right.directive';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

export interface DropInfo {
    instruction: GradingInstruction;
    tooltipMessage: string;
    removeMessage: string;
    feedbackHint: string;
}

type ApollonEditorHostElement = HTMLElement & { __apollonEditor?: ApollonEditor };

@Component({
    selector: 'jhi-modeling-assessment',
    templateUrl: './modeling-assessment.component.html',
    styleUrls: ['./modeling-assessment.component.scss'],
    imports: [ModelingExplanationEditorComponent, ApollonRailDisclosureComponent],
})
export class ModelingAssessmentComponent extends ModelingComponent implements AfterViewInit, OnDestroy {
    private artemisTranslatePipe = inject(ArtemisTranslatePipe);
    private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
    private readonly translateService = inject(TranslateService);

    private readonly topLeftRegion = viewChild<ElementRef<HTMLElement>>('topLeftRegion');
    private readonly topRightRegion = viewChild<ElementRef<HTMLElement>>('topRightRegion');
    private readonly bottomCenterRegion = viewChild<ElementRef<HTMLElement>>('bottomCenterRegion');
    // Read as directives, not elements: a slot that is present but unoccupied
    // must leave its region unmounted. See `ModelingAssessmentRegion`.
    private readonly projectedTopLeft = contentChild(ModelingAssessmentTopLeftDirective);
    private readonly projectedTopRight = contentChild(ModelingAssessmentTopRightDirective);
    protected readonly panelRegion = viewChild('panelRegion', { read: ElementRef<HTMLElement> });
    private readonly projectedPanel = contentChild(ModelingAssessmentPanelDirective);
    private panelRegionMounted = false;

    readonly enablePopups = input(true);
    readonly panelLabel = input('');
    protected readonly hasPanel = computed(() => isOccupied(this.projectedPanel()));
    /** Open by default: the feedback is what the assessed view exists to show. */
    readonly panelVisible = signal(true);
    protected readonly panelMaxHeight = signal(RAIL_DISCLOSURE_MAX_HEIGHT);
    protected readonly faPanel = faCommentDots;

    readonly highlightDifferences = input<boolean>();
    readonly resultFeedbacks = input<Feedback[]>();

    readonly feedbackChanged = output<Feedback[]>();
    readonly selectedElementIdsChanged = output<string[]>();

    readonly highlightedElements = input<Map<string, string> | undefined>(undefined);
    readonly elementCounts = input<ModelElementCount[]>();

    elementFeedback: Map<string, Feedback> = new Map<string, Feedback>();
    private shownInApollon: Map<string, string> = new Map<string, string>();
    referencedFeedbacks: Feedback[] = [];
    unreferencedFeedbacks: Feedback[] = [];
    firstCorrectionRoundColor = '#3e8acc';
    secondCorrectionRoundColor = '#ffa561';

    private modelChangeSubscription?: number;
    private assessmentSelectionSubscription?: number;
    private topLeftRegionMounted = false;
    private topRightRegionMounted = false;
    private bottomCenterRegionMounted = false;
    private chromeResizeObserver?: ResizeObserver;
    private panelResizeObserver?: ResizeObserver;
    private fitViewFrame?: number;
    private lastReservedPanelWidth = -1;
    /** Guards the one-off camera frame in {@link reserveRoomForPanel}. */
    private hasFramedForPanelInset = false;
    private readonly observedChromeResizeTargets = new Set<HTMLElement>();
    private chromeMountObserver?: MutationObserver;
    private chromeResizeFrame?: number;
    private isUpdatingFromServer = false;
    protected readonly bottomCenterElevated = signal(false);

    constructor() {
        super();
        this.translateService.onLangChange.pipe(takeUntilDestroyed()).subscribe(() => {
            this.apollonEditor?.setLabels(createApollonLabels(this.translateService));
        });
        effect(() => {
            this.highlightedElements();
            this.highlightDifferences();

            if (!this.apollonEditor) {
                return;
            }

            this.runHighlightUpdate();
        });
        effect(() => {
            const incoming = this.resultFeedbacks();

            if (!incoming || !this.apollonEditor) {
                return;
            }

            this.referencedFeedbacks = incoming.filter((feedbackElement) => feedbackElement.reference != undefined);
            this.updateElementFeedbackMapping(this.referencedFeedbacks);
            this.updateApollonAssessments(this.referencedFeedbacks);
        });

        effect(() => {
            const model = this.umlModel();

            if (!model || !this.apollonEditor) {
                return;
            }

            try {
                this.apollonEditor.model = normalizeApollonModel(model);
                this.handleFeedback();
            } catch (err) {
                captureException(err);
            }
        });
        effect(() => {
            isOccupied(this.projectedTopLeft());
            isOccupied(this.projectedTopRight());
            isOccupied(this.projectedPanel());
            this.panelRegion();
            this.explanation();
            this.topLeftRegion();
            this.topRightRegion();
            this.bottomCenterRegion();
            if (this.apollonEditor) {
                untracked(() => this.mountHostChrome());
            }
        });
    }

    async ngAfterViewInit(): Promise<void> {
        const resultFeedbacks = this.resultFeedbacks();
        if (resultFeedbacks !== undefined) {
            this.referencedFeedbacks = resultFeedbacks.filter((feedbackElement) => feedbackElement.reference != undefined);
            this.unreferencedFeedbacks = resultFeedbacks.filter(
                (feedbackElement) => feedbackElement.reference == undefined && feedbackElement.type === FeedbackType.MANUAL_UNREFERENCED,
            );
        }
        this.initializeApollonEditor();
        this.mountHostChrome();
        this.observeChromeLayout();
        const elementCounts = this.elementCounts();
        if (elementCounts) {
            await this.updateElementCounts(elementCounts);
        }
        this.updateApollonAssessments(this.referencedFeedbacks);
        this.applyStateConfiguration();
    }

    ngOnDestroy() {
        this.chromeResizeObserver?.disconnect();
        this.panelResizeObserver?.disconnect();
        if (this.fitViewFrame !== undefined) {
            window.cancelAnimationFrame(this.fitViewFrame);
        }
        this.observedChromeResizeTargets.clear();
        this.chromeMountObserver?.disconnect();
        if (this.chromeResizeFrame !== undefined) {
            window.cancelAnimationFrame(this.chromeResizeFrame);
        }

        const editor = this.apollonEditor;
        if (editor) {
            if (this.modelChangeSubscription !== undefined) {
                editor.unsubscribe(this.modelChangeSubscription);
                this.modelChangeSubscription = undefined;
            }
            if (this.assessmentSelectionSubscription !== undefined) {
                editor.unsubscribe(this.assessmentSelectionSubscription);
                this.assessmentSelectionSubscription = undefined;
            }
            this.apollonEditor = undefined;
            editor.destroy();
        }
        (this.elementRef.nativeElement as ApollonEditorHostElement).__apollonEditor = undefined;
    }

    private runHighlightUpdate(): void {
        this.updateApollonAssessments(this.referencedFeedbacks);
        this.applyStateConfiguration();
    }

    private initializeApollonEditor() {
        this.handleFeedback();
        const model = this.umlModel();

        this.apollonEditor = new ApollonEditor(this.editorContainer()!.nativeElement, {
            mode: ApollonMode.Assessment,
            readonly: this.readOnly(),
            model: model ? normalizeApollonModel(model) : undefined,
            type: this.diagramType() || UMLDiagramType.ClassDiagram,
            enablePopups: this.enablePopups(),
            scrollLock: false,
            labels: createApollonLabels(this.translateService),
        });

        (this.elementRef.nativeElement as ApollonEditorHostElement).__apollonEditor = this.apollonEditor;

        this.modelChangeSubscription = this.apollonEditor.subscribeToModelChange((state) => {
            if (!this.readOnly()) {
                const assessmentsArray = Object.values(state.assessments);
                this.referencedFeedbacks = this.generateFeedbackFromAssessment(assessmentsArray);
                this.feedbackChanged.emit(this.referencedFeedbacks);
            }
        });

        if (this.readOnly()) {
            this.assessmentSelectionSubscription = this.apollonEditor.subscribeToAssessmentSelection((selections) => this.selectedElementIdsChanged.emit(selections));
        }
    }

    private mountHostChrome(): void {
        if (!this.apollonEditor) {
            return;
        }

        this.topLeftRegionMounted = this.synchronizeHostRegion('top-left', this.topLeftRegion()?.nativeElement, isOccupied(this.projectedTopLeft()), this.topLeftRegionMounted);
        this.topRightRegionMounted = this.synchronizeHostRegion(
            'top-right',
            this.topRightRegion()?.nativeElement,
            isOccupied(this.projectedTopRight()),
            this.topRightRegionMounted,
        );
        // Hosting the panel in the rail keeps it inside Apollon's chrome, so it travels into
        // fullscreen with the canvas and is reachable without scrolling the page.
        const panel = this.panelRegion()?.nativeElement;
        const panelWasMounted = this.panelRegionMounted;
        this.panelRegionMounted = this.synchronizeHostRegion('right-rail', panel, isOccupied(this.projectedPanel()), this.panelRegionMounted);
        if (this.panelRegionMounted && !panelWasMounted) {
            // The rail clips its content, which would cut off a panel that hangs past its trigger.
            // Must follow the first `getRegionElement`, which is what creates the control.
            this.apollonEditor.updateControl('apollon:host:right-rail', { style: { overflow: 'visible' } });
            this.observePanelWidth(panel);
        }
        this.bottomCenterRegionMounted = this.synchronizeHostRegion(
            'bottom-center',
            this.bottomCenterRegion()?.nativeElement,
            !!this.explanation(),
            this.bottomCenterRegionMounted,
        );
        this.scheduleChromePlacement();
    }

    private synchronizeHostRegion(region: Parameters<ApollonEditor['getRegionElement']>[0], element: HTMLElement | undefined, hasContent: boolean, mounted: boolean): boolean {
        element?.classList.toggle('modeling-assessment__region--mounted', hasContent);
        if (element && hasContent && !mounted) {
            this.apollonEditor!.getRegionElement(region).append(element);
            return true;
        }
        if (element && !hasContent && mounted) {
            this.elementRef.nativeElement.prepend(element);
            this.apollonEditor!.releaseRegionElement(region);
            return false;
        }
        return mounted;
    }

    private observeChromeLayout(): void {
        const host = this.elementRef.nativeElement;
        if (typeof ResizeObserver === 'undefined') {
            return;
        }

        this.chromeResizeObserver = new ResizeObserver(() => this.scheduleChromePlacement());
        const bottomCenter = this.bottomCenterRegion()?.nativeElement;
        synchronizeResizeObserverTargets(this.chromeResizeObserver, this.observedChromeResizeTargets, [host, bottomCenter]);
        if (typeof MutationObserver !== 'undefined') {
            this.chromeMountObserver = new MutationObserver(() => this.scheduleChromePlacement());
            this.chromeMountObserver.observe(host, { childList: true, subtree: true });
        }
        this.scheduleChromePlacement();
    }

    /** The panel's width settles asynchronously as its content renders, so the reservation is watched, not taken once. */
    private observePanelWidth(panel: HTMLElement): void {
        if (typeof ResizeObserver === 'undefined') {
            return;
        }
        this.panelResizeObserver?.disconnect();
        this.panelResizeObserver = new ResizeObserver(() => this.reserveRoomForPanel(panel));
        this.panelResizeObserver.observe(panel);
        this.reserveRoomForPanel(panel);
    }

    /**
     * The panel floats over the canvas, so the rail's own inset only covers the trigger and the
     * camera would frame the diagram behind the open panel. Reserving the measured panel width as
     * an explicit inset keeps the float while laying the diagram out clear of it.
     */
    private reserveRoomForPanel(panel: HTMLElement): void {
        if (!this.apollonEditor) {
            return;
        }
        const openPanel = panel.querySelector<HTMLElement>('.apollon-rail-disclosure__panel:not([hidden])');
        const width = Math.ceil(openPanel?.getBoundingClientRect().width ?? 0);
        if (width === this.lastReservedPanelWidth) {
            return;
        }
        this.lastReservedPanelWidth = width;
        this.apollonEditor.updateControl('apollon:host:right-rail', {
            style: { overflow: 'visible' },
            // 'auto' alone measures only the in-flow trigger; the float needs saying.
            inset: width > 0 ? { right: width } : 'auto',
        });
        // Apollon fits on mount, before this inset exists, so one refit corrects that first frame.
        // Never again: the viewport is the reader's, and they toggle this panel while assessing.
        if (!this.hasFramedForPanelInset) {
            this.hasFramedForPanelInset = true;
            this.scheduleFitView();
        }
    }

    /** Two frames out: the overlay engine measures a region on the frame after it resizes, so refitting sooner uses the previous inset. */
    private scheduleFitView(): void {
        if (this.fitViewFrame !== undefined) {
            window.cancelAnimationFrame(this.fitViewFrame);
        }
        this.fitViewFrame = window.requestAnimationFrame(() => {
            this.fitViewFrame = window.requestAnimationFrame(() => {
                this.fitViewFrame = undefined;
                this.apollonEditor?.fitView({ respectInsets: true });
            });
        });
    }

    protected onPanelVisibilityChanged(): void {
        const panel = this.panelRegion()?.nativeElement;
        if (panel) {
            // A frame out, so the rail is re-reserved against the panel's new visibility rather than its old one.
            window.requestAnimationFrame(() => this.reserveRoomForPanel(panel));
        }
    }

    protected scheduleChromePlacement(): void {
        if (this.chromeResizeFrame !== undefined || !this.apollonEditor) {
            return;
        }

        this.chromeResizeFrame = window.requestAnimationFrame(() => {
            this.chromeResizeFrame = undefined;
            this.updateBottomCenterPlacement();
            this.updatePanelMaxHeight();
        });
    }

    private updatePanelMaxHeight(): void {
        const apollonRoot = this.elementRef.nativeElement.querySelector<HTMLElement>('.apollon-editor');
        this.panelMaxHeight.set(
            measureRailDisclosureMaxHeight(apollonRoot, this.panelRegion()?.nativeElement, this.panelVisible(), [
                this.bottomCenterRegion()?.nativeElement,
                apollonRoot?.querySelector<HTMLElement>('[data-apollon-control="apollon:zoom"]'),
                apollonRoot?.querySelector<HTMLElement>('[data-apollon-control="apollon:minimap"]'),
            ]),
        );
    }

    /** The open panel floats over the canvas, so bottom-center has to dodge it. */
    private panelObstruction(): DOMRect | undefined {
        const panel = this.panelRegion()?.nativeElement;
        return panel && !panel.hidden ? panel.getBoundingClientRect() : undefined;
    }

    private updateBottomCenterPlacement(): void {
        const bottomCenter = this.bottomCenterRegion()?.nativeElement;
        const apollonRoot = this.elementRef.nativeElement.querySelector<HTMLElement>('.apollon-editor');
        const bottomCenterRegion = bottomCenter?.closest<HTMLElement>('[data-apollon-region="bottom-center"]');
        const zoom = apollonRoot?.querySelector<HTMLElement>('[data-apollon-control="apollon:zoom"]');
        const minimap = apollonRoot?.querySelector<HTMLElement>('[data-apollon-control="apollon:minimap"]');
        const persistentSurface = bottomCenter?.querySelector<HTMLElement>('jhi-modeling-explanation-editor');
        if (!apollonRoot || !bottomCenterRegion || !bottomCenter || !zoom || !minimap || !persistentSurface) {
            this.resetBottomCenterPlacement(bottomCenter);
            return;
        }

        if (this.chromeResizeObserver) {
            synchronizeResizeObserverTargets(this.chromeResizeObserver, this.observedChromeResizeTargets, [
                this.elementRef.nativeElement,
                apollonRoot,
                bottomCenterRegion,
                bottomCenter,
                zoom,
                minimap,
                persistentSurface,
            ]);
        }

        const bottomCenterStyle = getComputedStyle(bottomCenter);
        const editorStyle = getComputedStyle(apollonRoot);
        const placement = calculateBottomCenterPlacement({
            root: apollonRoot.getBoundingClientRect(),
            zoom: zoom.getBoundingClientRect(),
            minimap: minimap.getBoundingClientRect(),
            surface: persistentSurface.getBoundingClientRect(),
            obstruction: this.panelObstruction(),
            chromeGap: Number.parseFloat(editorStyle.getPropertyValue('--apollon-chrome-gap')) || 0,
            chromeEdge: Number.parseFloat(editorStyle.getPropertyValue('--apollon-chrome-edge')) || 0,
            rootFontSize: Number.parseFloat(getComputedStyle(document.documentElement).fontSize) || 16,
            previousShift: Number.parseFloat(bottomCenterStyle.getPropertyValue('--modeling-assessment-bottom-center-shift-x')) || 0,
        });

        applyBottomCenterPlacement(bottomCenter, '--modeling-assessment-bottom-center-shift-x', placement);
        this.bottomCenterElevated.set(placement.elevated);
    }

    private resetBottomCenterPlacement(bottomCenter?: HTMLElement): void {
        clearBottomCenterPlacement(bottomCenter, '--modeling-assessment-bottom-center-shift-x');
        this.bottomCenterElevated.set(false);
    }

    private applyStateConfiguration() {
        this.updateHighlightedElements(this.highlightedElements());
    }

    generateFeedbackFromAssessment(assessments: Assessment[]): Feedback[] {
        for (const assessment of assessments) {
            const dropInfo = assessment.dropInfo as GradingInstruction | undefined;
            const instruction = dropInfo?.id ? dropInfo : undefined;
            let feedback = this.elementFeedback.get(assessment.modelElementId);
            if (feedback) {
                const scoreChanged = feedback.credits !== assessment.score;
                if (scoreChanged && feedback.gradingInstruction) {
                    feedback.gradingInstruction = undefined;
                }
                feedback.credits = assessment.score;
                if (Feedback.isFeedbackSuggestion(feedback)) {
                    const alreadyAdapted = feedback.text?.startsWith(FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER);
                    if (alreadyAdapted) {
                        if (assessment.feedback !== undefined) {
                            feedback.detailText = assessment.feedback;
                        }
                    } else {
                        const lastShown = this.shownInApollon.get(assessment.modelElementId);
                        const textChanged = assessment.feedback !== undefined && lastShown !== undefined && assessment.feedback !== lastShown;
                        if (textChanged || scoreChanged) {
                            const originalTitle = this.stripSuggestionPrefix(feedback.text ?? '');
                            feedback.text = FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER + originalTitle;
                            if (textChanged && assessment.feedback !== undefined) {
                                feedback.detailText = assessment.feedback;
                                this.shownInApollon.set(assessment.modelElementId, assessment.feedback);
                            }
                        }
                    }
                } else {
                    feedback.text = assessment.feedback;
                }
                if (instruction?.id) {
                    feedback.gradingInstruction = instruction;
                }
                if (feedback.gradingInstruction && assessment.dropInfo == undefined) {
                    feedback.gradingInstruction = undefined;
                }
            } else {
                feedback = Feedback.forModeling(assessment.score, assessment.feedback, assessment.modelElementId, assessment.elementType, assessment.dropInfo as DropInfo);
                this.elementFeedback.set(assessment.modelElementId, feedback);
            }
        }

        if (!this.isUpdatingFromServer) {
            const currentIds = new Set(assessments.map((a) => a.modelElementId));
            for (const id of this.elementFeedback.keys()) {
                if (!currentIds.has(id)) {
                    this.elementFeedback.delete(id);
                    this.shownInApollon.delete(id);
                }
            }
        }

        return assessments.map((a) => this.elementFeedback.get(a.modelElementId)!).filter(Boolean);
    }

    private handleFeedback(): void {
        const feedbacks = this.resultFeedbacks();
        if (feedbacks !== undefined) {
            this.referencedFeedbacks = filterInvalidFeedback(feedbacks, this.umlModel());
            this.updateElementFeedbackMapping(this.referencedFeedbacks);
            this.updateApollonAssessments(this.referencedFeedbacks);
        }
    }

    private updateElementFeedbackMapping(feedbacks: Feedback[]) {
        this.elementFeedback = new Map(feedbacks.filter((feedback) => feedback.referenceId !== undefined).map((feedback) => [feedback.referenceId!, feedback]));
    }

    private updateHighlightedElements(newElements: Map<string, string> | undefined): void {
        // Apollon treats undefined as unchanged; null clears existing highlights.
        this.apollonEditor?.setElementHighlights(newElements ?? null);
    }

    /**
     * Sends the canvas to one element and opens its feedback, so a feedback list
     * beside the diagram can answer "where does this apply?". `undefined` clears
     * the selection again.
     */
    revealAssessment(elementId: string | undefined): void {
        this.apollonEditor?.revealAssessment(elementId ?? null);
    }

    private async updateElementCounts(newElementCounts: ModelElementCount[]) {
        if (!newElementCounts) {
            return;
        }

        const elementCountMap = new Map<string, number>();

        newElementCounts.forEach((elementCount) => elementCountMap.set(elementCount.elementId, elementCount.numberOfOtherElements));

        if (this.apollonEditor != undefined) {
            const model: UMLModel = this.apollonEditor.model;
            for (const node of model.nodes) {
                node.data.assessmentNote = this.calculateNote(elementCountMap.get(node.id));
            }
            for (const edge of model.edges) {
                edge.data.assessmentNote = this.calculateNote(elementCountMap.get(edge.id));
            }
            this.apollonEditor.model = model;
        }
    }

    private updateApollonAssessments(feedbacks: Feedback[]): void {
        const editor = this.apollonEditor;
        if (!editor) {
            return;
        }
        this.isUpdatingFromServer = true;

        try {
            const assessments = feedbacks.map((feedback): Assessment => {
                const feedbackContent = Feedback.isFeedbackSuggestion(feedback) ? (feedback.detailText ?? '') : (feedback.text ?? '');
                this.shownInApollon.set(feedback.referenceId!, feedbackContent);
                return {
                    modelElementId: feedback.referenceId!,
                    elementType: feedback.referenceType!,
                    score: feedback.credits ?? 0,
                    feedback: feedbackContent,
                    label: this.calculateLabel(feedback),
                    labelColor: this.calculateLabelColor(feedback),
                    correctionStatus: this.calculateCorrectionStatusForFeedback(feedback),
                    dropInfo: this.calculateDropInfo(feedback),
                };
            });

            const incomingIds = new Set(assessments.map((assessment) => assessment.modelElementId));
            for (const id of this.shownInApollon.keys()) {
                if (!incomingIds.has(id)) {
                    this.shownInApollon.delete(id);
                }
            }

            const currentModel = editor.model;
            const hasRemovedAssessment = Object.keys(currentModel.assessments ?? {}).some((id) => !incomingIds.has(id));
            if (hasRemovedAssessment) {
                // Apollon can upsert assessments individually but removal requires replacing the complete model.
                editor.model = cloneWith(currentModel, {
                    assessments: Object.fromEntries(assessments.map((assessment) => [assessment.modelElementId, assessment])),
                });
                return;
            }

            for (const assessment of assessments) {
                editor.addOrUpdateAssessment(assessment);
            }
        } finally {
            this.isUpdatingFromServer = false;
        }
    }

    private calculateLabel(feedback: Feedback) {
        const firstCorrectionRoundText = this.artemisTranslatePipe.transform('artemisApp.assessment.diffView.correctionRoundDiffFirst');
        const secondCorrectionRoundText = this.artemisTranslatePipe.transform('artemisApp.assessment.diffView.correctionRoundDiffSecond');
        if (this.highlightDifferences()) {
            return feedback.copiedFeedbackId ? firstCorrectionRoundText : secondCorrectionRoundText;
        }
        return undefined;
    }

    private calculateLabelColor(feedback: Feedback) {
        if (this.highlightDifferences()) {
            return feedback.copiedFeedbackId ? this.firstCorrectionRoundColor : this.secondCorrectionRoundColor;
        }
        return '';
    }

    private calculateNote(count: number | undefined) {
        if (count) {
            return this.artemisTranslatePipe.transform('artemisApp.modelingAssessment.impactWarning', { affectedSubmissionsCount: count });
        }

        return undefined;
    }

    private calculateCorrectionStatusForFeedback(feedback: Feedback) {
        let correctionStatusDescription = feedback.correctionStatus
            ? this.artemisTranslatePipe.transform('artemisApp.exampleSubmission.feedback.' + feedback.correctionStatus)
            : feedback.correctionStatus;
        if (feedback.correctionStatus && feedback.correctionStatus !== 'CORRECT') {
            correctionStatusDescription += ' ⚠️';
        }
        let correctionStatus: 'CORRECT' | 'INCORRECT' | 'NOT_VALIDATED';
        switch (feedback.correctionStatus) {
            case 'CORRECT':
                correctionStatus = 'CORRECT';
                break;
            case undefined:
                correctionStatus = 'NOT_VALIDATED';
                break;
            default:
                correctionStatus = 'INCORRECT';
        }

        return {
            description: correctionStatusDescription,
            status: correctionStatus,
        };
    }

    private stripSuggestionPrefix(text: string): string {
        for (const prefix of [FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER, FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER, FEEDBACK_SUGGESTION_IDENTIFIER]) {
            if (text.startsWith(prefix)) {
                return text.slice(prefix.length);
            }
        }
        return text;
    }

    private calculateDropInfo(feedback: Feedback) {
        if (feedback.gradingInstruction) {
            return feedback.gradingInstruction;
        }

        return undefined;
    }
}
