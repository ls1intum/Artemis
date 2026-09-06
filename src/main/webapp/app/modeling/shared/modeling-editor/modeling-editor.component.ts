import {
    AfterViewInit,
    Component,
    ElementRef,
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
import { ApollonEditor, ApollonMode, type CollaborationUser, UMLDiagramType, UMLModel } from '@tumaet/apollon';
import { faAlignLeft, faCheck, faCircleNotch, faDownLeftAndUpRightToCenter, faTimes, faUpRightAndDownLeftFromCenter } from '@fortawesome/free-solid-svg-icons';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';
import { ModelingComponent } from 'app/modeling/shared/modeling/modeling.component';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ModelingExplanationEditorComponent } from '../modeling-explanation-editor/modeling-explanation-editor.component';
import { captureException } from '@sentry/angular';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { normalizeApollonModel } from 'app/modeling/shared/apollon-model.util';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateService } from '@ngx-translate/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ContentObserver } from '@angular/cdk/observers';
import { Subscription } from 'rxjs';
import { ApollonRailDisclosureComponent } from 'app/modeling/shared/modeling-editor/apollon-rail-disclosure/apollon-rail-disclosure.component';
import {
    RAIL_DISCLOSURE_MAX_HEIGHT,
    applyBottomCenterPlacement,
    calculateBottomCenterPlacement,
    clearBottomCenterPlacement,
    measureRailDisclosureMaxHeight,
    synchronizeResizeObserverTargets,
} from 'app/modeling/shared/modeling-editor/apollon-chrome-placement';
import { FullscreenPresentationService } from 'app/modeling/shared/fullscreen/fullscreen-presentation.service';
import { createApollonLabels } from 'app/modeling/shared/modeling-editor/apollon-labels';
import { ModelingEditorHelpComponent } from 'app/modeling/shared/modeling-editor/modeling-editor-help.component';
import { ModelingEditorBottomCenterDirective } from 'app/modeling/shared/modeling-editor/modeling-editor-bottom-center.directive';
import { ModelingEditorTopLeftDirective } from 'app/modeling/shared/modeling-editor/modeling-editor-top-left.directive';

type ApollonEditorE2eHostElement = HTMLElement & { __apollonEditor?: ApollonEditor };

@Component({
    selector: 'jhi-modeling-editor',
    templateUrl: './modeling-editor.component.html',
    styleUrls: ['./modeling-editor.component.scss'],
    encapsulation: ViewEncapsulation.None,
    host: { '(document:fullscreenchange)': 'onFullscreenChange()' },
    imports: [
        TranslateDirective,
        ArtemisTranslatePipe,
        FaIconComponent,
        ModelingExplanationEditorComponent,
        MarkdownDirective,
        ModelingEditorHelpComponent,
        ApollonRailDisclosureComponent,
    ],
})
export class ModelingEditorComponent extends ModelingComponent implements AfterViewInit, OnDestroy {
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faEnterFullscreen = faUpRightAndDownLeftFromCenter;
    protected readonly faExitFullscreen = faDownLeftAndUpRightToCenter;
    protected readonly faProblemStatement = faAlignLeft;
    protected readonly farQuestionCircle = faQuestionCircle;
    protected readonly fullscreenSupported = document.fullscreenEnabled;

    private readonly sanitizer = inject(DomSanitizer);
    private readonly elementRef = inject(ElementRef);
    private readonly translateService = inject(TranslateService);
    private readonly fullscreenPresentation = inject(FullscreenPresentationService);
    private readonly contentObserver = inject(ContentObserver);

    protected readonly editorFrame = viewChild<ElementRef<HTMLElement>>('editorFrame');
    private readonly editorActions = viewChild<ElementRef<HTMLElement>>('editorActions');
    private readonly editorTopLeftRegion = viewChild<ElementRef<HTMLElement>>('editorTopLeftRegion');
    private readonly editorBottomCenter = viewChild<ElementRef<HTMLElement>>('editorBottomCenter');
    private readonly editorProblemStatement = viewChild('editorProblemStatement', { read: ElementRef<HTMLElement> });
    private readonly problemStatementDisclosure = viewChild<ApollonRailDisclosureComponent>('editorProblemStatement');
    private readonly projectedTopLeft = contentChild(ModelingEditorTopLeftDirective, { read: ElementRef });
    private readonly projectedBottomCenter = contentChild(ModelingEditorBottomCenterDirective, { read: ElementRef });
    protected readonly hasEditorTopLeft = computed(() => !!this.projectedTopLeft());
    private readonly hasEditorTopLeftRegion = computed(() => (!!this.savedStatus() && !this.readOnly()) || this.hasEditorTopLeft());
    protected readonly hasEditorBottomCenter = computed(() => this.withExplanation() || (this.showProjectedBottomCenter() && !!this.projectedBottomCenter()));

    readonly helpVisible = signal(false);
    readonly fullscreenActive = signal(false);
    readonly problemStatementVisible = signal(false);
    private openProblemStatementOnFullscreenEntry = false;
    protected readonly problemStatementMaxHeight = signal(RAIL_DISCLOSURE_MAX_HEIGHT);
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
    private chromeMountSubscription?: Subscription;
    private chromeResizeFrame: number | undefined;
    private readOnlyExportRevision = 0;

    readonly readOnlySVG = signal<SafeHtml | undefined>(undefined);

    constructor() {
        super();
        effect(() => {
            const fullscreenActive = this.fullscreenActive();
            const problemStatement = this.problemStatement();
            if (this.openProblemStatementOnFullscreenEntry && fullscreenActive && problemStatement?.trim()) {
                this.problemStatementVisible.set(true);
                this.openProblemStatementOnFullscreenEntry = false;
            }
        });
        this.translateService.onLangChange.pipe(takeUntilDestroyed()).subscribe(() => {
            this.apollonEditor?.setLabels(createApollonLabels(this.translateService));
        });

        effect(() => {
            const diagramType = this.diagramType();
            const readOnly = this.readOnly();

            if (this.isDestroyed || !this.viewInitialized || !diagramType) {
                return;
            }

            if (readOnly) {
                this.destroyApollonEditor();
                return;
            }

            this.initializeApollonEditor();
        });

        effect(() => {
            const enabled = this.collaborationEnabled();
            const user = this.collaborationUser();
            const readOnly = this.readOnly();

            if (this.isDestroyed || readOnly || !enabled || !user) {
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
            const readOnly = this.readOnly();

            if (this.isDestroyed || !this.viewInitialized || !model) {
                return;
            }

            if (readOnly) {
                void this.renderReadOnlyDiagram(model);
                return;
            }

            if (!this.apollonEditor) {
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
                untracked(() => {
                    this.mountEditorRegions();
                    this.scheduleChromePlacement();
                });
            }
        });
    }

    async ngAfterViewInit(): Promise<void> {
        this.viewInitialized = true;
        if (this.readOnly()) {
            const model = this.umlModel();
            if (model) {
                await this.renderReadOnlyDiagram(model);
            }
            return;
        }

        this.initializeApollonEditor();
        this.observeChromeLayout();
    }

    private initializeApollonEditor(): void {
        if (this.readOnly()) {
            return;
        }

        const collaborationEnabled = untracked(() => this.collaborationEnabled()) && !this.readOnly();
        const collaborationUser = collaborationEnabled ? untracked(() => this.collaborationUser()) : undefined;

        if (collaborationEnabled && !collaborationUser) {
            return;
        }

        this.destroyApollonEditor();

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
            this.observeChromeLayout();

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

    /** Reparents projected content before Apollon removes the released region from the DOM. */
    private releaseHostRegion(region: Parameters<ApollonEditor['getRegionElement']>[0], element: HTMLElement | undefined): void {
        if (element) {
            this.editorFrame()?.nativeElement.prepend(element);
        }
        this.apollonEditor?.releaseRegionElement(region);
    }

    private destroyApollonEditor(): void {
        const editor = this.apollonEditor;
        if (editor) {
            if (this.modelSubscription !== undefined) {
                editor.unsubscribe(this.modelSubscription);
                this.modelSubscription = undefined;
            }
            if (this.topRightRegionMounted) {
                this.releaseHostRegion('top-right', this.editorActions()?.nativeElement);
                this.topRightRegionMounted = false;
            }
            if (this.topLeftRegionMounted) {
                this.releaseHostRegion('top-left', this.editorTopLeftRegion()?.nativeElement);
                this.topLeftRegionMounted = false;
            }
            if (this.bottomCenterMounted) {
                this.releaseHostRegion('bottom-center', this.editorBottomCenter()?.nativeElement);
                this.bottomCenterMounted = false;
            }
            if (this.problemStatementRegionMounted) {
                this.releaseHostRegion('right-rail', this.editorProblemStatement()?.nativeElement);
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

    private async renderReadOnlyDiagram(model: UMLModel): Promise<void> {
        const revision = ++this.readOnlyExportRevision;
        this.readOnlySVG.set(undefined);
        try {
            const diagram = await ApollonEditor.exportModelAsSvg(ModelingEditorComponent.modelWithoutAssessments(model));
            if (!this.isDestroyed && revision === this.readOnlyExportRevision) {
                this.readOnlySVG.set(this.sanitizer.bypassSecurityTrustHtml(diagram.svg));
            }
        } catch (error) {
            if (!this.isDestroyed && revision === this.readOnlyExportRevision) {
                captureException(error);
            }
        }
    }

    getCurrentModel(): UMLModel {
        return ModelingEditorComponent.modelWithoutAssessments(this.apollonEditor!.model);
    }

    openHelp(): void {
        this.helpVisible.set(true);
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
        const shouldMountTopLeft = !!topLeft && (this.hasEditorTopLeftRegion() || hasProjectedTopLeft);
        if (shouldMountTopLeft && !this.topLeftRegionMounted) {
            this.apollonEditor.getRegionElement('top-left').append(topLeft);
            this.topLeftRegionMounted = true;
        } else if (!shouldMountTopLeft && this.topLeftRegionMounted) {
            this.releaseHostRegion('top-left', topLeft);
            this.topLeftRegionMounted = false;
        }

        const bottomCenter = this.editorBottomCenter()?.nativeElement;
        if (this.bottomCenterMounted && (!bottomCenter || !this.hasEditorBottomCenter())) {
            this.releaseHostRegion('bottom-center', bottomCenter);
            this.bottomCenterMounted = false;
        }
        if (bottomCenter && this.hasEditorBottomCenter() && !this.bottomCenterMounted) {
            this.apollonEditor.getRegionElement('bottom-center').append(bottomCenter);
            this.bottomCenterMounted = true;
        }

        const problemStatement = this.editorProblemStatement()?.nativeElement;
        const shouldMountProblemStatement = this.fullscreenActive() && !!this.problemStatement()?.trim();
        if (this.problemStatementRegionMounted && (!problemStatement || !shouldMountProblemStatement)) {
            this.releaseHostRegion('right-rail', problemStatement);
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
        if (!editorFrame || this.chromeResizeObserver) {
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
        this.chromeMountSubscription?.unsubscribe();
        this.chromeMountSubscription = editorFrame ? this.contentObserver.observe(editorFrame).subscribe(() => this.scheduleChromePlacement()) : undefined;
    }

    private observeChromeResizeTargets(...elements: Array<HTMLElement | null | undefined>): void {
        const observer = this.chromeResizeObserver;
        const editorFrame = this.editorFrame()?.nativeElement;
        if (!observer || !editorFrame) {
            return;
        }
        synchronizeResizeObserverTargets(observer, this.observedChromeResizeTargets, [editorFrame, ...elements]);
    }

    protected scheduleChromePlacement(): void {
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
        const apollonRoot = this.editorFrame()?.nativeElement?.querySelector<HTMLElement>('.apollon-editor');
        const problemStatement = this.editorProblemStatement()?.nativeElement;
        const maxHeight = measureRailDisclosureMaxHeight(apollonRoot, problemStatement, this.problemStatementVisible(), [
            this.editorBottomCenter()?.nativeElement,
            apollonRoot?.querySelector<HTMLElement>('[data-apollon-control="apollon:zoom"]'),
            apollonRoot?.querySelector<HTMLElement>('[data-apollon-control="apollon:minimap"]'),
        ]);
        this.problemStatementMaxHeight.set(maxHeight);
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
        const placement = calculateBottomCenterPlacement({
            root: apollonRoot.getBoundingClientRect(),
            zoom: zoom.getBoundingClientRect(),
            minimap: minimap.getBoundingClientRect(),
            surface: placementControl.getBoundingClientRect(),
            palette: paletteRect,
            paletteRegion,
            obstruction: this.problemStatementDisclosure()?.getVisiblePanelRect(),
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

    /**
     * Apollon measures its overlay insets one frame behind a resize, and `fitView` snapshots them when it applies.
     * Two frames is therefore the earliest a refit can see the inset this resize produced.
     */
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
        if (!this.fullscreenPresentation.promote(editorFrame, () => this.escapeFullscreen())) {
            return false;
        }
        this.openProblemStatementOnFullscreenEntry = true;
        this.fullscreenActive.set(true);
        this.apollonEditor?.setScrollLock(false);
        return true;
    }

    /** Exits fullscreen when navigation hides or destroys the promoted frame. */
    private escapeFullscreen(): void {
        const wasFullscreen = document.fullscreenElement === document.documentElement;
        this.restoreFullscreenPresentation();
        if (wasFullscreen) {
            void document.exitFullscreen().catch(captureException);
        }
    }

    private restoreFullscreenPresentation(): void {
        const editorFrame = this.editorFrame()?.nativeElement;
        if (!this.fullscreenActive() && !this.fullscreenPresentation.owns(editorFrame)) {
            return;
        }

        this.problemStatementVisible.set(false);
        this.openProblemStatementOnFullscreenEntry = false;
        if (this.problemStatementRegionMounted) {
            this.releaseHostRegion('right-rail', this.editorProblemStatement()?.nativeElement);
            this.problemStatementRegionMounted = false;
        }

        this.fullscreenPresentation.restore();
        this.fullscreenActive.set(false);
        this.apollonEditor?.setScrollLock(this.scrollLock());
    }

    ngOnDestroy(): void {
        this.isDestroyed = true;
        this.readOnlyExportRevision++;
        this.chromeResizeObserver?.disconnect();
        this.chromeResizeObserver = undefined;
        this.observedChromeResizeTargets.clear();
        this.chromeMountSubscription?.unsubscribe();
        this.chromeMountSubscription = undefined;
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
