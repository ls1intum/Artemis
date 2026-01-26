import { AfterViewInit, Component, OnDestroy, ViewEncapsulation, effect, inject, input, output } from '@angular/core';
import { ApollonEditor, ApollonMode, SVG, UMLDiagramType, UMLModel } from '@tumaet/apollon';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { isFullScreen } from 'app/shared/util/fullscreen.util';
import { faCheck, faCircleNotch, faTimes } from '@fortawesome/free-solid-svg-icons';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';
import { ModelingComponent } from 'app/modeling/shared/modeling/modeling.component';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass, NgStyle } from '@angular/common';
import { ModelingExplanationEditorComponent } from '../modeling-explanation-editor/modeling-explanation-editor.component';
import { captureException } from '@sentry/angular';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

@Component({
    selector: 'jhi-modeling-editor',
    templateUrl: './modeling-editor.component.html',
    styleUrls: ['./modeling-editor.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [TranslateDirective, FaIconComponent, NgStyle, NgClass, ModelingExplanationEditorComponent, HtmlForMarkdownPipe],
})
export class ModelingEditorComponent extends ModelingComponent implements AfterViewInit, OnDestroy {
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly farQuestionCircle = faQuestionCircle;

    private readonly modalService = inject(NgbModal);
    private readonly sanitizer = inject(DomSanitizer);

    showHelpButton = input(true);
    withExplanation = input(false);
    savedStatus = input<{
        isChanged?: boolean;
        isSaving?: boolean;
    }>();

    onModelChanged = output<UMLModel>();
    onModelPatch = output<string>();

    private modelSubscription: number;
    private isDestroyed = false;

    readonlyApollonDiagram?: SVG;
    readOnlySVG?: SafeHtml;

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
                    this.readOnlySVG = this.sanitizer.bypassSecurityTrustHtml(this.readonlyApollonDiagram.svg);
                }
            }
        } else {
            this.setupInteract();
        }
    }

    /**
     * This function initializes the Apollon editor in Modeling mode.
     */
    private initializeApollonEditor(): void {
        if (this.apollonEditor) {
            this.apollonEditor.unsubscribe(this.modelSubscription);
            this.apollonEditor.destroy();
        }

        // Apollon doesn't need assessments in Modeling mode
        const umlModel = this.umlModel();
        if (umlModel) {
            ModelingEditorComponent.removeAssessments(umlModel);
        }

        const editorContainer = this.editorContainer();
        if (editorContainer) {
            this.apollonEditor = new ApollonEditor(editorContainer.nativeElement, {
                model: umlModel,
                mode: ApollonMode.Modelling,
                readonly: this.readOnly(),
                type: this.diagramType() || UMLDiagramType.ClassDiagram,
                scale: 0.8,
            });

            this.modelSubscription = this.apollonEditor.subscribeToModelChange((model: UMLModel) => {
                this.onModelChanged.emit(model);
            });

            this.apollonEditor.sendBroadcastMessage((patch) => {
                this.onModelPatch.emit(patch);
            });
        }
    }

    /**
     * Destroys the Apollon editor instance, unsubscribes from events, and removes event listeners to clean up resources.
     */
    private destroyApollonEditor(): void {
        if (this.apollonEditor) {
            if (this.modelSubscription) {
                this.apollonEditor.unsubscribe(this.modelSubscription);
            }
            this.apollonEditor.destroy();
            this.apollonEditor = undefined;
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
     * This function opens the modal for the help dialog.
     */
    open(content: any): void {
        this.modalService.open(content, { size: 'lg' });
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
        // Support both Apollon v4 (nodes) and v3 (elements) formats
        const elements = umlModel.nodes ?? (umlModel as any).elements ?? {};
        return Object.values(elements).find((element: any) => element.name?.trim() === name && element.type === 'Class');
    }

    /**
     * Return the UMLModelElement of the type ClassAttribute with the @param attribute
     * @param attribute the name of the attribute
     * @param umlModel the UML model to search in
     */
    elementWithAttribute(attribute: string, umlModel: UMLModel) {
        // Support both Apollon v4 (nodes) and v3 (elements) formats
        const elements = umlModel.nodes ?? (umlModel as any).elements ?? {};
        return Object.values(elements).find((element: any) => element.name?.includes(attribute) && element.type === 'ClassAttribute');
    }

    /**
     * Return the UMLModelElement of the type ClassMethod with the @param method
     * @param method the name of the method
     * @param umlModel the UML model to search in
     */
    elementWithMethod(method: string, umlModel: UMLModel) {
        // Support both Apollon v4 (nodes) and v3 (elements) formats
        const elements = umlModel.nodes ?? (umlModel as any).elements ?? {};
        return Object.values(elements).find((element: any) => element.name?.includes(method) && element.type === 'ClassMethod');
    }

    /**
     * Import a patch into the Apollon editor
     * @param patch the patch to import
     */
    importPatch(patch: string) {
        this.apollonEditor?.receiveBroadcastedMessage(patch);
    }
}
