import { AfterViewInit, Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, ViewEncapsulation, inject } from '@angular/core';
import { ApollonEditor, ApollonMode, SVG, UMLDiagramType, UMLElementType, UMLModel } from '@ls1intum/apollon';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { isFullScreen } from 'app/shared/util/fullscreen.util';
import { faCheck, faCircleNotch, faTimes } from '@fortawesome/free-solid-svg-icons';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';
import { ModelingComponent } from 'app/modeling/shared/modeling/modeling.component';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { Patch } from '@ls1intum/apollon';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass, NgStyle } from '@angular/common';
import { captureException } from '@sentry/angular';
import { FormsModule } from '@angular/forms';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

@Component({
    selector: 'jhi-modeling-editor',
    templateUrl: './modeling-editor.component.html',
    styleUrls: ['./modeling-editor.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [TranslateDirective, FaIconComponent, NgStyle, NgClass, FormsModule, HtmlForMarkdownPipe],
})
export class ModelingEditorComponent extends ModelingComponent implements AfterViewInit, OnDestroy, OnChanges {
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly farQuestionCircle = faQuestionCircle;

    private readonly modalService = inject(NgbModal);
    private readonly sanitizer = inject(DomSanitizer);

    @Input() showHelpButton = true;
    @Input() withExplanation = false;
    @Input() savedStatus?: { isChanged?: boolean; isSaving?: boolean };

    @Output() private onModelChanged: EventEmitter<UMLModel> = new EventEmitter<UMLModel>();
    @Output() onModelPatch = new EventEmitter<Patch>();

    @Output() explanationChange = new EventEmitter();

    private modelSubscription: number;
    private modelPatchSubscription: number;

    readonlyApollonDiagram?: SVG;
    readOnlySVG?: SafeHtml;

    constructor() {
        super();
    }

    /**
     * Initializes the Apollon editor.
     * If resizeOptions is set to true, resizes the editor according to interactions.
     */
    async ngAfterViewInit(): Promise<void> {
        this.initializeApollonEditor();
        if (this.readOnly) {
            await this.apollonEditor?.nextRender;
            this.readonlyApollonDiagram = await this.apollonEditor?.exportAsSVG();
            if (this.readonlyApollonDiagram?.svg) {
                this.readOnlySVG = this.sanitizer.bypassSecurityTrustHtml(this.readonlyApollonDiagram.svg);
            }

            // Destroy the Apollon editor after exporting the SVG, to avoid SVG <marker> id collisions
            this.destroyApollonEditor();
        } else {
            this.setupInteract();
        }
    }

    /**
     * This function initializes the Apollon editor in Modeling mode.
     */
    private initializeApollonEditor(): void {
        if (this.apollonEditor) {
            this.apollonEditor.unsubscribeFromModelChange(this.modelSubscription);
            this.apollonEditor.unsubscribeFromModelChangePatches(this.modelPatchSubscription);
            this.apollonEditor.destroy();
        }

        // Apollon doesn't need assessments in Modeling mode
        ModelingEditorComponent.removeAssessments(this.umlModel);

        if (this.editorContainer) {
            this.apollonEditor = new ApollonEditor(this.editorContainer.nativeElement, {
                model: this.umlModel,
                mode: ApollonMode.Modelling,
                readonly: this.readOnly,
                type: this.diagramType || UMLDiagramType.ClassDiagram,
                scale: 0.8,
            });

            this.modelSubscription = this.apollonEditor.subscribeToModelChange((model: UMLModel) => {
                this.onModelChanged.emit(model);
            });

            this.modelPatchSubscription = this.apollonEditor.subscribeToModelChangePatches((patch: Patch) => {
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
                this.apollonEditor.unsubscribeFromModelChange(this.modelSubscription);
            }
            if (this.modelPatchSubscription) {
                this.apollonEditor.unsubscribeFromModelChangePatches(this.modelPatchSubscription);
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
     * If changes are made to the uml model, update the model and remove assessments
     * @param {SimpleChanges} changes - Changes made
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (changes.diagramType) {
            // if the diagram type changed -> recreate the editor
            this.initializeApollonEditor();
        }

        if (changes.umlModel && changes.umlModel.currentValue && this.apollonEditor) {
            this.umlModel = changes.umlModel.currentValue;
            // Apollon doesn't need assessments in Modeling mode
            ModelingEditorComponent.removeAssessments(this.umlModel);
            this.apollonEditor.model = this.umlModel;
        }
    }

    /**
     * If the apollon editor is not null, destroy it and set it to null, on component destruction
     */
    ngOnDestroy(): void {
        try {
            this.destroyApollonEditor();
        } catch (err) {
            captureException(err);
        }
    }

    /**
     * Return the UMLModelElement of the type class with the @param name
     * @param name class name
     * @param umlModel current model that is assessed
     */
    elementWithClass(name: string, umlModel: UMLModel) {
        return Object.values(umlModel.elements).find((element) => element.name.trim() === name && element.type === UMLElementType.Class);
    }

    /**
     * Return the UMLModelElement of the type ClassAttribute with the @param attribute
     * @param attribute name
     * @param umlModel current model that is assessed
     */
    elementWithAttribute(attribute: string, umlModel: UMLModel) {
        return Object.values(umlModel.elements).find((element) => element.name.includes(attribute) && element.type === UMLElementType.ClassAttribute);
    }

    /**
     * Return the UMLModelElement of the type ClassMethod with the @param method
     * @param method name
     * @param umlModel current model that is assessed
     */
    elementWithMethod(method: string, umlModel: UMLModel) {
        return Object.values(umlModel.elements).find((element) => element.name.includes(method) && element.type === UMLElementType.ClassMethod);
    }

    /**
     * checks if this component is the current fullscreen component
     */
    get isFullScreen() {
        return isFullScreen();
    }

    // Emit explanation change when textarea input changes
    onExplanationInput(newValue: string) {
        this.explanationChange.emit(newValue);
        this.explanation = newValue;
    }

    /**
     * Import a patch into the Apollon editor
     * @param patch the patch to import
     */
    importPatch(patch: Patch) {
        this.apollonEditor?.importPatch(patch);
    }
}
