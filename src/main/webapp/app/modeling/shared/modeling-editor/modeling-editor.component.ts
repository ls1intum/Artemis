import {
    AfterViewInit,
    Component,
    ElementRef,
    HostListener,
    OnDestroy,
    ViewEncapsulation,
    computed,
    contentChild,
    effect,
    inject,
    input,
    output,
    signal,
    untracked,
    viewChild,
} from '@angular/core';
import { ApollonEditor, ApollonMode, type CollaborationUser, SVG, UMLDiagramType, UMLModel } from '@tumaet/apollon';
import {
    faAlignLeft,
    faCheck,
    faChevronDown,
    faChevronUp,
    faCircleNotch,
    faDownLeftAndUpRightToCenter,
    faTimes,
    faUpRightAndDownLeftFromCenter,
} from '@fortawesome/free-solid-svg-icons';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';
import { ModelingComponent } from 'app/modeling/shared/modeling/modeling.component';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ModelingExplanationEditorComponent } from '../modeling-explanation-editor/modeling-explanation-editor.component';
import { captureException } from '@sentry/angular';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { normalizeApollonModel } from 'app/modeling/shared/apollon-model.util';
import { ResizableDirective, type ResizableSizeEvent } from 'app/shared-ui/directives/resizable.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateService } from '@ngx-translate/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
    applyBottomCenterPlacement,
    calculateBottomCenterPlacement,
    clearBottomCenterPlacement,
    synchronizeResizeObserverTargets,
} from 'app/modeling/shared/modeling-editor/apollon-chrome-placement';
import { FullscreenPresentationService } from 'app/modeling/shared/fullscreen/fullscreen-presentation.service';
import { createApollonLabels } from 'app/modeling/shared/modeling-editor/apollon-labels';
import { ModelingEditorHelpComponent } from 'app/modeling/shared/modeling-editor/modeling-editor-help.component';
import { ModelingEditorBottomCenterDirective } from 'app/modeling/shared/modeling-editor/modeling-editor-bottom-center.directive';
import { ModelingEditorTopLeftDirective } from 'app/modeling/shared/modeling-editor/modeling-editor-top-left.directive';

type ApollonEditorE2eHostElement = HTMLElement & { __apollonEditor?: ApollonEditor };

let nextProblemStatementId = 0;

@Component({
    selector: 'jhi-modeling-editor',
    templateUrl: './modeling-editor.component.html',
    styleUrls: ['./modeling-editor.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [TranslateDirective, ArtemisTranslatePipe, FaIconComponent, ModelingExplanationEditorComponent, MarkdownDirective, ModelingEditorHelpComponent, ResizableDirective],
})
export class ModelingEditorComponent extends ModelingComponent implements AfterViewInit, OnDestroy {
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faEnterFullscreen = faUpRightAndDownLeftFromCenter;
    protected readonly faExitFullscreen = faDownLeftAndUpRightToCenter;
    protected readonly faProblemStatement = faAlignLeft;
    protected readonly faExpandProblemStatement = faChevronDown;
    protected readonly faCollapseProblemStatement = faChevronUp;
    protected readonly farQuestionCircle = faQuestionCircle;
    protected readonly fullscreenSupported = document.fullscreenEnabled !== false;
    protected readonly problemStatementPanelId = `modeling-editor-problem-statement-${++nextProblemStatementId}`;

    private readonly sanitizer = inject(DomSanitizer);
    private readonly elementRef = inject(ElementRef);
    private readonly translateService = inject(TranslateService);
    private readonly fullscreenPresentation = inject(FullscreenPresentationService);

    protected readonly editorFrame = viewChild<ElementRef<HTMLElement>>('editorFrame');
    private readonly editorActions = viewChild<ElementRef<HTMLElement>>('editorActions');
    private readonly editorTopLeftRegion = viewChild<ElementRef<HTMLElement>>('editorTopLeftRegion');
    private readonly editorBottomCenter = viewChild<ElementRef<HTMLElement>>('editorBottomCenter');
    private readonly editorProblemStatement = viewChild<ElementRef<HTMLElement>>('editorProblemStatement');
    private readonly projectedTopLeft = contentChild(ModelingEditorTopLeftDirective, { read: ElementRef });
    private readonly projectedBottomCenter = contentChild(ModelingEditorBottomCenterDirective, { read: ElementRef });
    protected readonly hasEditorTopLeft = computed(() => !!this.projectedTopLeft());
    private readonly hasEditorTopLeftRegion = computed(() => (!!this.savedStatus() && !this.readOnly()) || this.hasEditorTopLeft());
    protected readonly hasEditorBottomCenter = computed(() => this.withExplanation() || (this.showProjectedBottomCenter() && !!this.projectedBottomCenter()));

    readonly helpVisible = signal(false);
    readonly fullscreenActive = signal(false);
    readonly problemStatementVisible = signal(false);
    protected readonly problemStatementWidth = signal(416);
    protected readonly problemStatementHeight = signal(480);
    private readonly problemStatementMaxHeight = signal(720);
    protected readonly problemStatementResizeConstraints = computed(() => ({ minWidth: 288, maxWidth: 704, minHeight: 224, maxHeight: this.problemStatementMaxHeight() }));
    protected readonly bottomCenterElevated = signal(false);

    readonly showHelpButton = input(true);
    readonly showFullscreenButton = input(true);
    readonly problemStatement = input<string | undefined>();
    readonly tile = input(false);
    readonly withExplanation = input(false);
    readonly showProjectedBottomCenter = input(true);
    readonly scrollLock = input(false);
    readonly collaborationEnabled = input(false);
    readonly collaborationUser = input<CollaborationUser | undefined>(undefined);
    readonly savedStatus = input<{
        isChanged?: boolean;
        isSaving?: boolean;
    }>();

    readonly onModelChanged = output<UMLModel>();
    readonly onModelPatch = output<string>();

    private modelSubscription: number | undefined;
    private isDestroyed = false;
    private viewInitialized = false;
    private topRightRegionMounted = false;
    private topLeftRegionMounted = false;
    private bottomCenterMounted = false;
    private problemStatementRegionMounted = false;
    private chromeResizeObserver: ResizeObserver | undefined;
    private readonly observedChromeResizeTargets = new Set<HTMLElement>();
    private chromeMountObserver: MutationObserver | undefined;
    private chromeResizeFrame: number | undefined;

    readonlyApollonDiagram?: SVG;
    readonly readOnlySVG = signal<SafeHtml | undefined>(undefined);

    constructor() {
        super();
        this.translateService.onLangChange.pipe(takeUntilDestroyed()).subscribe(() => {
            this.apollonEditor?.setLabels(createApollonLabels(this.translateService));
        });

        effect(() => {
            const diagramType = this.diagramType();

            if (this.isDestroyed || !this.viewInitialized || !diagramType) {
                return;
            }

            this.initializeApollonEditor();
        });

        effect(() => {
            const enabled = this.collaborationEnabled();
            const user = this.collaborationUser();

            if (this.isDestroyed || !enabled || !user) {
                return;
            }

            if (!this.apollonEditor) {
                this.initializeApollonEditor();
                return;
            }

            try {
                this.apollonEditor.setLocalAwarenessUser(user);
            } catch (err) {
                captureException(err);
            }
        });

        effect(() => {
            const model = this.umlModel();

            if (this.isDestroyed || !model || !this.apollonEditor) {
                return;
            }

            try {
                this.apollonEditor.model = ModelingEditorComponent.modelWithoutAssessments(model);
            } catch (err) {
                captureException(err);
            }
        });

        effect(() => {
            this.hasEditorTopLeft();
            this.hasEditorTopLeftRegion();
            this.hasEditorBottomCenter();
            this.editorTopLeftRegion();
            this.editorBottomCenter();
            this.editorProblemStatement();
            this.problemStatementVisible();
            this.problemStatement();
            this.fullscreenActive();
            this.bottomCenterElevated();
            if (!this.isDestroyed && this.apollonEditor) {
                untracked(() => this.mountEditorRegions());
            }
        });
    }

    async ngAfterViewInit(): Promise<void> {
        this.viewInitialized = true;
        this.initializeApollonEditor();
        this.observeChromeLayout();
        if (this.readOnly()) {
            if (this.apollonEditor) {
                this.readonlyApollonDiagram = await this.apollonEditor.exportAsSVG();
                if (this.readonlyApollonDiagram?.svg) {
                    this.readOnlySVG.set(this.sanitizer.bypassSecurityTrustHtml(this.readonlyApollonDiagram.svg));
                }
            }
        }
    }

    private initializeApollonEditor(): void {
        const collaborationEnabled = untracked(() => this.collaborationEnabled()) && !this.readOnly();
        const collaborationUser = collaborationEnabled ? untracked(() => this.collaborationUser()) : undefined;

        // Apollon creates its collaboration layer at construction, so wait until the local user is available.
        if (collaborationEnabled && !collaborationUser) {
            return;
        }

        this.destroyApollonEditor();

        // Model updates have a dedicated effect; tracking them here would rebuild the collaboration session.
        const inputModel = untracked(() => this.umlModel());
        const umlModel = inputModel ? ModelingEditorComponent.modelWithoutAssessments(inputModel) : undefined;

        const editorContainer = this.editorContainer();
        if (editorContainer) {
            this.apollonEditor = new ApollonEditor(editorContainer.nativeElement, {
                model: umlModel,
                mode: ApollonMode.Modelling,
                readonly: this.readOnly(),
                scrollLock: this.fullscreenActive() ? false : this.scrollLock(),
                type: this.diagramType() || UMLDiagramType.ClassDiagram,
                labels: createApollonLabels(this.translateService),
                collaboration: collaborationEnabled
                    ? {
                          enabled: true,
                          user: collaborationUser,
                          showPresence: true,
                          showCursors: true,
                          showSelectionHighlights: true,
                          showFollow: true,
                      }
                    : undefined,
            });

            (this.elementRef.nativeElement as ApollonEditorE2eHostElement).__apollonEditor = this.apollonEditor;
            this.mountEditorRegions();
            this.observeChromeMount();

            this.modelSubscription = this.apollonEditor.subscribeToModelChange((model: UMLModel) => {
                if (this.isDestroyed) {
                    return;
                }
                this.onModelChanged.emit(model);
            });

            this.apollonEditor.sendBroadcastMessage((patch) => {
                if (this.isDestroyed) {
                    return;
                }
                this.onModelPatch.emit(patch);
            });
        }
    }

    private destroyApollonEditor(): void {
        const editor = this.apollonEditor;
        if (editor) {
            if (this.modelSubscription !== undefined) {
                editor.unsubscribe(this.modelSubscription);
                this.modelSubscription = undefined;
            }
            if (this.topRightRegionMounted) {
                editor.releaseRegionElement('top-right');
                this.topRightRegionMounted = false;
            }
            if (this.topLeftRegionMounted) {
                editor.releaseRegionElement('top-left');
                this.topLeftRegionMounted = false;
            }
            if (this.bottomCenterMounted) {
                editor.releaseRegionElement('bottom-center');
                this.bottomCenterMounted = false;
            }
            if (this.problemStatementRegionMounted) {
                editor.releaseRegionElement('right-rail');
                this.problemStatementRegionMounted = false;
            }
            this.apollonEditor = undefined;
            (this.elementRef.nativeElement as ApollonEditorE2eHostElement).__apollonEditor = undefined;
            editor.destroy();
        }
    }

    get isApollonEditorMounted(): boolean {
        return this.apollonEditor != undefined;
    }

    private static modelWithoutAssessments(umlModel: UMLModel): UMLModel {
        const copy = normalizeApollonModel(umlModel);
        copy.assessments = {};
        return copy;
    }

    getCurrentModel(): UMLModel {
        return ModelingEditorComponent.modelWithoutAssessments(this.apollonEditor!.model);
    }

    openHelp(): void {
        this.helpVisible.set(true);
    }

    protected toggleProblemStatement(): void {
        if (!this.fullscreenActive() || !this.problemStatement()?.trim()) {
            return;
        }

        this.problemStatementVisible.update((visible) => !visible);
        this.scheduleChromePlacement();
    }

    protected resizeProblemStatement({ width, height }: ResizableSizeEvent): void {
        this.problemStatementWidth.set(width);
        this.problemStatementHeight.set(height);
        this.scheduleChromePlacement();
    }

    private mountEditorRegions(): void {
        const actions = this.editorActions()?.nativeElement;
        if (!actions || !this.apollonEditor || this.readOnly()) {
            return;
        }

        if (!this.topRightRegionMounted) {
            this.apollonEditor.getRegionElement('top-right').append(actions);
            this.topRightRegionMounted = true;
        }

        const topLeft = this.editorTopLeftRegion()?.nativeElement;
        const hasProjectedTopLeft = !!topLeft?.querySelector('.modeling-editor__top-left')?.childElementCount;
        if (topLeft && (this.hasEditorTopLeftRegion() || hasProjectedTopLeft)) {
            this.apollonEditor.getRegionElement('top-left').append(topLeft);
            this.topLeftRegionMounted = true;
        } else if (this.topLeftRegionMounted) {
            this.apollonEditor.releaseRegionElement('top-left');
            this.topLeftRegionMounted = false;
        }

        const bottomCenter = this.editorBottomCenter()?.nativeElement;
        if (this.bottomCenterMounted && (!bottomCenter || !this.hasEditorBottomCenter())) {
            this.apollonEditor.releaseRegionElement('bottom-center');
            this.bottomCenterMounted = false;
        }
        if (bottomCenter && this.hasEditorBottomCenter() && !this.bottomCenterMounted) {
            this.apollonEditor.getRegionElement('bottom-center').append(bottomCenter);
            this.bottomCenterMounted = true;
        }

        const problemStatement = this.editorProblemStatement()?.nativeElement;
        const shouldMountProblemStatement = this.fullscreenActive() && !!this.problemStatement()?.trim();
        if (this.problemStatementRegionMounted && (!problemStatement || !shouldMountProblemStatement)) {
            this.apollonEditor.releaseRegionElement('right-rail');
            this.problemStatementRegionMounted = false;
        }
        if (problemStatement && shouldMountProblemStatement && !this.problemStatementRegionMounted) {
            const rightRail = this.apollonEditor.getRegionElement('right-rail');
            this.apollonEditor.updateControl('apollon:host:right-rail', { style: { overflow: 'visible' } });
            rightRail.append(problemStatement);
            this.problemStatementRegionMounted = true;
        }
    }

    private observeChromeLayout(): void {
        const editorFrame = this.editorFrame()?.nativeElement;
        if (!editorFrame || typeof ResizeObserver === 'undefined') {
            return;
        }

        this.chromeResizeObserver = new ResizeObserver(() => this.scheduleChromePlacement());
        this.chromeResizeObserver.observe(editorFrame);
        this.observedChromeResizeTargets.add(editorFrame);
        this.observeChromeMount();
        this.scheduleChromePlacement();
    }

    private observeChromeMount(): void {
        const editorFrame = this.editorFrame()?.nativeElement;
        this.chromeMountObserver?.disconnect();
        this.chromeMountObserver = undefined;
        if (!editorFrame || typeof MutationObserver === 'undefined') {
            return;
        }

        this.chromeMountObserver = new MutationObserver(() => this.scheduleChromePlacement());
        this.chromeMountObserver.observe(editorFrame, { childList: true, subtree: true });
    }

    private observeChromeResizeTargets(...elements: Array<HTMLElement | null | undefined>): void {
        const observer = this.chromeResizeObserver;
        const editorFrame = this.editorFrame()?.nativeElement;
        if (!observer || !editorFrame) {
            return;
        }
        synchronizeResizeObserverTargets(observer, this.observedChromeResizeTargets, [editorFrame, ...elements]);
    }

    private scheduleChromePlacement(): void {
        if (this.chromeResizeFrame !== undefined || this.isDestroyed) {
            return;
        }

        this.chromeResizeFrame = window.requestAnimationFrame(() => {
            this.chromeResizeFrame = undefined;
            this.updateChromePlacement();
        });
    }

    private updateChromePlacement(): void {
        this.updateBottomCenterPlacement();
        this.updateProblemStatementMaxHeight();
    }

    private updateProblemStatementMaxHeight(): void {
        const editorFrame = this.editorFrame()?.nativeElement;
        const problemStatement = this.editorProblemStatement()?.nativeElement;
        const apollonRoot = editorFrame?.querySelector<HTMLElement>('.apollon-editor');
        const trigger = problemStatement?.querySelector<HTMLElement>('.modeling-editor__problem-statement-trigger-island');
        if (!editorFrame || !apollonRoot || !problemStatement || !trigger || !this.problemStatementVisible()) {
            this.problemStatementMaxHeight.set(720);
            return;
        }

        const rootRect = apollonRoot.getBoundingClientRect();
        const triggerRect = trigger.getBoundingClientRect();
        const styles = getComputedStyle(apollonRoot);
        const chromeGap = Number.parseFloat(styles.getPropertyValue('--apollon-chrome-gap')) || 0;
        const chromeEdge = Number.parseFloat(styles.getPropertyValue('--apollon-chrome-edge')) || 0;
        const panelRight = triggerRect.right;
        const panelLeft = Math.max(rootRect.left, panelRight - this.problemStatementWidth());
        let boundary = rootRect.bottom - chromeEdge;

        const bottomChrome = [
            this.editorBottomCenter()?.nativeElement,
            apollonRoot.querySelector<HTMLElement>('[data-apollon-control="apollon:zoom"]'),
            apollonRoot.querySelector<HTMLElement>('[data-apollon-control="apollon:minimap"]'),
        ];
        for (const control of bottomChrome) {
            if (!control || control.hidden) {
                continue;
            }
            const rect = control.getBoundingClientRect();
            const intersectsHorizontally = panelLeft < rect.right && panelRight > rect.left;
            if (rect.width > 0 && rect.height > 0 && rect.top > triggerRect.bottom && intersectsHorizontally) {
                boundary = Math.min(boundary, rect.top - chromeGap);
            }
        }

        this.problemStatementMaxHeight.set(Math.max(224, Math.min(720, Math.floor(boundary - triggerRect.bottom))));
    }

    private updateBottomCenterPlacement(): void {
        const editorFrame = this.editorFrame()?.nativeElement;
        const bottomCenter = this.editorBottomCenter()?.nativeElement;
        const apollonRoot = editorFrame?.querySelector<HTMLElement>('.apollon-editor');
        const bottomCenterRegion = bottomCenter?.closest<HTMLElement>('[data-apollon-region="bottom-center"]');
        const palette = apollonRoot?.querySelector<HTMLElement>('[data-apollon-control="apollon:palette"]');
        const zoom = apollonRoot?.querySelector<HTMLElement>('[data-apollon-control="apollon:zoom"]');
        const minimap = apollonRoot?.querySelector<HTMLElement>('[data-apollon-control="apollon:minimap"]');
        const persistentSurface = bottomCenter?.querySelector<HTMLElement>('jhi-modeling-explanation-editor, jhi-modeling-markdown-explanation-editor');
        const placementControl = persistentSurface;
        if (!editorFrame || !apollonRoot || !bottomCenterRegion || !bottomCenter || !zoom || !minimap || !placementControl) {
            this.resetBottomCenterPlacement(bottomCenter);
            return;
        }

        this.observeChromeResizeTargets(apollonRoot, bottomCenterRegion, bottomCenter, palette, zoom, minimap, placementControl);

        const bottomCenterStyle = getComputedStyle(bottomCenter);
        const editorStyle = getComputedStyle(editorFrame);
        const paletteRect = palette?.getBoundingClientRect();
        const paletteRegion = palette?.closest<HTMLElement>('[data-apollon-region]')?.dataset.apollonRegion;
        const problemStatement = this.editorProblemStatement()?.nativeElement;
        const placement = calculateBottomCenterPlacement({
            root: apollonRoot.getBoundingClientRect(),
            zoom: zoom.getBoundingClientRect(),
            minimap: minimap.getBoundingClientRect(),
            surface: placementControl.getBoundingClientRect(),
            palette: paletteRect,
            paletteRegion,
            obstruction: problemStatement && !problemStatement.hidden ? problemStatement.getBoundingClientRect() : undefined,
            chromeGap: Number.parseFloat(editorStyle.getPropertyValue('--apollon-chrome-gap')) || 0,
            chromeEdge: Number.parseFloat(editorStyle.getPropertyValue('--apollon-chrome-edge')) || 0,
            rootFontSize: Number.parseFloat(getComputedStyle(document.documentElement).fontSize) || 16,
            previousShift: Number.parseFloat(bottomCenterStyle.getPropertyValue('--modeling-editor-bottom-center-shift-x')) || 0,
        });
        applyBottomCenterPlacement(bottomCenter, '--modeling-editor-bottom-center-shift-x', placement);
        this.bottomCenterElevated.set(placement.elevated);
    }

    private resetBottomCenterPlacement(bottomCenter?: HTMLElement): void {
        clearBottomCenterPlacement(bottomCenter, '--modeling-editor-bottom-center-shift-x');
        this.bottomCenterElevated.set(false);
    }

    private refitAfterFullscreenLayoutSettles(): void {
        window.requestAnimationFrame(() => window.requestAnimationFrame(() => this.apollonEditor?.fitView()));
    }

    // Document-root fullscreen keeps body-portaled Apollon interactions inside the fullscreen subtree.
    async toggleFullscreen(): Promise<void> {
        const editorFrame = this.editorFrame()?.nativeElement;
        if (!editorFrame) {
            return;
        }

        if (this.fullscreenActive()) {
            try {
                await document.exitFullscreen();
            } catch (error) {
                captureException(error);
            }
            return;
        }

        if (document.fullscreenElement) {
            return;
        }

        if (!this.prepareFullscreenPresentation(editorFrame)) {
            return;
        }
        try {
            await document.documentElement.requestFullscreen();
        } catch (error) {
            this.restoreFullscreenPresentation();
            captureException(error);
        }
    }

    @HostListener('document:fullscreenchange')
    protected onFullscreenChange(): void {
        const ownsPresentation = this.fullscreenPresentation.owns(this.editorFrame()?.nativeElement);
        const ownsFullscreen = ownsPresentation && document.fullscreenElement === document.documentElement;
        if (ownsFullscreen) {
            this.fullscreenActive.set(true);
        } else if (ownsPresentation) {
            this.restoreFullscreenPresentation();
        }

        if (ownsPresentation) {
            this.refitAfterFullscreenLayoutSettles();
        }

        this.scheduleChromePlacement();
    }

    private prepareFullscreenPresentation(editorFrame: HTMLElement): boolean {
        if (!this.fullscreenPresentation.promote(editorFrame)) {
            return false;
        }
        this.fullscreenActive.set(true);
        this.problemStatementVisible.set(!!this.problemStatement()?.trim());
        this.apollonEditor?.setScrollLock(false);
        editorFrame.classList.add('modeling-editor__frame--fullscreen');
        return true;
    }

    private restoreFullscreenPresentation(): void {
        const editorFrame = this.editorFrame()?.nativeElement;
        if (!this.fullscreenPresentation.owns(editorFrame)) {
            return;
        }

        this.problemStatementVisible.set(false);
        if (this.problemStatementRegionMounted) {
            this.apollonEditor?.releaseRegionElement('right-rail');
            this.problemStatementRegionMounted = false;
        }

        this.fullscreenPresentation.restore();
        editorFrame?.classList.remove('modeling-editor__frame--fullscreen');
        this.fullscreenActive.set(false);
        this.apollonEditor?.setScrollLock(this.scrollLock());
    }

    ngOnDestroy(): void {
        this.isDestroyed = true;
        this.chromeResizeObserver?.disconnect();
        this.chromeResizeObserver = undefined;
        this.observedChromeResizeTargets.clear();
        this.chromeMountObserver?.disconnect();
        this.chromeMountObserver = undefined;
        if (this.chromeResizeFrame !== undefined) {
            window.cancelAnimationFrame(this.chromeResizeFrame);
            this.chromeResizeFrame = undefined;
        }
        try {
            const shouldExitFullscreen = this.fullscreenPresentation.owns(this.editorFrame()?.nativeElement) && document.fullscreenElement === document.documentElement;
            this.restoreFullscreenPresentation();
            if (shouldExitFullscreen) {
                void document.exitFullscreen().catch(captureException);
            }
            this.destroyApollonEditor();
        } catch (err) {
            captureException(err);
        }
    }

    importPatch(patch: string) {
        this.apollonEditor?.receiveBroadcastedMessage(patch);
    }

    resynchronizeCollaborationAfterReconnect(): void {
        this.apollonEditor?.broadcastFullState();
        const user = this.collaborationUser();
        if (!user) {
            return;
        }
        try {
            this.apollonEditor?.setLocalAwarenessUser(user);
        } catch (err) {
            captureException(err);
        }
    }
}
