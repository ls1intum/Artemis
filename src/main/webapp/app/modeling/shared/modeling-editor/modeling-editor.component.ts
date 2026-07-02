import { AfterViewInit, Component, ElementRef, OnDestroy, ViewEncapsulation, effect, inject, input, output, signal, untracked } from '@angular/core';
import { ApollonEditor, ApollonMode, type CollaborationUser, SVG, UMLDiagramType, UMLModel } from '@tumaet/apollon';
import { DialogModule } from 'primeng/dialog';
import { isFullScreen } from 'app/foundation/util/fullscreen.util';
import { faCheck, faCircleNotch, faTimes } from '@fortawesome/free-solid-svg-icons';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';
import { ModelingComponent } from 'app/modeling/shared/modeling/modeling.component';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ModelingExplanationEditorComponent } from '../modeling-explanation-editor/modeling-explanation-editor.component';
import { captureException } from '@sentry/angular';
import { HtmlForMarkdownPipe } from 'app/foundation/pipes/html-for-markdown.pipe';
import { getModelNodes } from 'app/modeling/shared/apollon-model.util';
import { ResizableDirective } from 'app/shared-ui/directives/resizable.directive';

/** Host element augmented with the Apollon editor instance exposed for E2E test access. */
type ApollonEditorHostElement = HTMLElement & { __apollonEditor?: ApollonEditor };

@Component({
    selector: 'jhi-modeling-editor',
    templateUrl: './modeling-editor.component.html',
    styleUrls: ['./modeling-editor.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [TranslateDirective, FaIconComponent, ModelingExplanationEditorComponent, HtmlForMarkdownPipe, DialogModule, ResizableDirective],
})
export class ModelingEditorComponent extends ModelingComponent implements AfterViewInit, OnDestroy {
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly farQuestionCircle = faQuestionCircle;

    private readonly sanitizer = inject(DomSanitizer);
    private readonly elementRef = inject(ElementRef);

    readonly helpVisible = signal(false);

    showHelpButton = input(true);
    withExplanation = input(false);
    scrollLock = input(false);
    collaborationEnabled = input(false);
    collaborationUser = input<CollaborationUser | undefined>(undefined);
    savedStatus = input<{
        isChanged?: boolean;
        isSaving?: boolean;
    }>();

    onModelChanged = output<UMLModel>();
    onModelPatch = output<string>();

    private modelSubscription: number | undefined;
    private isDestroyed = false;

    readonlyApollonDiagram?: SVG;
    readonly readOnlySVG = signal<SafeHtml | undefined>(undefined);

    constructor() {
        super();
        effect(() => {
            const diagramType = this.diagramType();

            if (this.isDestroyed || !diagramType || !this.editorContainer()) {
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
                // work on a copy if removeAssessments mutates
                const umlModel = { ...model } as UMLModel;
                ModelingEditorComponent.removeAssessments(umlModel);
                this.apollonEditor.model = umlModel;
            } catch (err) {
                // Editor may not be fully initialized yet or already destroyed
                captureException(err);
            }
        });
    }

    /**
     * Initializes the Apollon editor.
     * If resizeOptions is set to true, resizes the editor according to interactions.
     */
    async ngAfterViewInit(): Promise<void> {
        this.initializeApollonEditor();
        if (this.readOnly()) {
            if (this.apollonEditor) {
                this.readonlyApollonDiagram = await this.apollonEditor.exportAsSVG();
                if (this.readonlyApollonDiagram?.svg) {
                    this.readOnlySVG.set(this.sanitizer.bypassSecurityTrustHtml(this.readonlyApollonDiagram.svg));
                }
            }
        }
    }

    /**
     * This function initializes the Apollon editor in Modeling mode.
     */
    private initializeApollonEditor(): void {
        // Read collaboration inputs untracked: the dedicated collaboration effect owns mount-on-user-resolve,
        // so construction must not also re-trigger (and rebuild the editor) when the local user signal arrives.
        const collaborationEnabled = untracked(() => this.collaborationEnabled()) && !this.readOnly();
        const collaborationUser = collaborationEnabled ? untracked(() => this.collaborationUser()) : undefined;

        // Apollon determines whether to mount its collaboration UI from the initial options.
        // Wait for the local user instead of constructing an editor whose presence layer stays inactive.
        if (collaborationEnabled && !collaborationUser) {
            return;
        }

        if (this.apollonEditor) {
            if (this.modelSubscription !== undefined) {
                this.apollonEditor.unsubscribe(this.modelSubscription);
            }
            this.apollonEditor.destroy();
        }

        // Read the seed model untracked so construction (owned by the diagramType effect) does not re-run on
        // every model update — that would tear down the live Yjs/collaboration session. Ongoing updates are
        // applied to the existing editor by the dedicated umlModel effect. Assessments are stripped because
        // Apollon doesn't need them in Modelling mode.
        const umlModel = untracked(() => this.umlModel());
        if (umlModel) {
            ModelingEditorComponent.removeAssessments(umlModel);
        }

        const editorContainer = this.editorContainer();
        if (editorContainer) {
            this.apollonEditor = new ApollonEditor(editorContainer.nativeElement, {
                model: umlModel,
                mode: ApollonMode.Modelling,
                readonly: this.readOnly(),
                scrollLock: this.scrollLock(),
                type: this.diagramType() || UMLDiagramType.ClassDiagram,
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

            // Expose the ApollonEditor instance on the host DOM element for E2E test access.
            // In production mode, ng.getComponent() is not available, so tests use this property instead.
            (this.elementRef.nativeElement as ApollonEditorHostElement).__apollonEditor = this.apollonEditor;

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

    /**
     * Destroys the Apollon editor instance, unsubscribes from events, and removes event listeners to clean up resources.
     */
    private destroyApollonEditor(): void {
        if (this.apollonEditor) {
            if (this.modelSubscription !== undefined) {
                this.apollonEditor.unsubscribe(this.modelSubscription);
            }
            this.apollonEditor.destroy();
            this.apollonEditor = undefined;
            (this.elementRef.nativeElement as ApollonEditorHostElement).__apollonEditor = undefined;
        }
    }

    get isApollonEditorMounted(): boolean {
        return this.apollonEditor != undefined;
    }

    /**
     * Removes the Assessments from a given UMLModel. In modeling mode the assessments are not needed.
     * Also, they should not be sent to the server and persisted as part of the model JSON.
     *
     * @param umlModel the model for which the assessments should be removed
     */
    private static removeAssessments(umlModel: UMLModel) {
        if (umlModel) {
            umlModel.assessments = {};
        }
    }

    /**
     * Returns the current model of the Apollon editor. It removes the assessment first, as it should not be part
     * of the model outside Apollon.
     */
    getCurrentModel(): UMLModel {
        const currentModel = this.apollonEditor!.model;
        ModelingEditorComponent.removeAssessments(currentModel);
        return currentModel;
    }

    /**
     * Opens the help dialog.
     */
    openHelp(): void {
        this.helpVisible.set(true);
    }

    /**
     * If the apollon editor is not null, destroy it and set it to null, on component destruction
     */
    ngOnDestroy(): void {
        this.isDestroyed = true;
        try {
            this.destroyApollonEditor();
        } catch (err) {
            captureException(err);
        }
    }

    /**
     * checks if this component is the current fullscreen component
     */
    get isFullScreen() {
        return isFullScreen();
    }

    /**
     * Return the UMLModelElement of the type Class with the @param name
     * @param name the name of the UML class
     * @param umlModel the UML model to search in
     */
    elementWithClass(name: string, umlModel: UMLModel) {
        return getModelNodes(umlModel).find((element) => typeof element.name === 'string' && element.name.trim() === name && element.type === 'Class');
    }

    /**
     * Return the UMLModelElement of the type ClassAttribute with the @param attribute
     * @param attribute the name of the attribute
     * @param umlModel the UML model to search in
     */
    elementWithAttribute(attribute: string, umlModel: UMLModel) {
        return getModelNodes(umlModel).find((element) => typeof element.name === 'string' && element.name.includes(attribute) && element.type === 'ClassAttribute');
    }

    /**
     * Return the UMLModelElement of the type ClassMethod with the @param method
     * @param method the name of the method
     * @param umlModel the UML model to search in
     */
    elementWithMethod(method: string, umlModel: UMLModel) {
        return getModelNodes(umlModel).find((element) => typeof element.name === 'string' && element.name.includes(method) && element.type === 'ClassMethod');
    }

    /**
     * Import a patch into the Apollon editor
     * @param patch the patch to import
     */
    importPatch(patch: string) {
        this.apollonEditor?.receiveBroadcastedMessage(patch);
    }

    // Re-announce the full Yjs document state to peers. Needed on (re)connect because Apollon
    // broadcasts only incremental updates; a peer that missed a window of edits would otherwise
    // stay out of sync until the next local edit.
    broadcastFullState(): void {
        this.apollonEditor?.broadcastFullState();
    }

    // Re-announce the local user's presence to peers. Apollon emits awareness only on local awareness
    // changes, so after a reconnect a peer that pruned us on the awareness timeout would not see us again
    // until our next cursor or selection change.
    reannounceLocalAwareness(): void {
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
