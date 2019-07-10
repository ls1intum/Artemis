import { AfterViewInit, Component, ElementRef, Input, OnDestroy, Renderer2, ViewChild, OnChanges, SimpleChanges } from '@angular/core';
import { ApollonEditor, ApollonMode, DiagramType, UMLModel } from '@ls1intum/apollon';
import { JhiAlertService } from 'ng-jhipster';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import interact from 'interactjs';

@Component({
    selector: 'jhi-modeling-editor',
    templateUrl: './modeling-editor.component.html',
    styleUrls: ['./modeling-editor.component.scss'],
})
export class ModelingEditorComponent implements AfterViewInit, OnDestroy, OnChanges {
    @ViewChild('editorContainer', { static: false })
    editorContainer: ElementRef;
    @ViewChild('resizeContainer', { static: false })
    resizeContainer: ElementRef;
    @Input()
    umlModel: UMLModel;
    @Input()
    diagramType: DiagramType;
    @Input()
    readOnly = false;
    @Input()
    resizeOptions: { initialWidth: string; maxWidth?: number };

    private apollonEditor: ApollonEditor | null = null;

    constructor(private jhiAlertService: JhiAlertService, private renderer: Renderer2, private modalService: NgbModal) {}

    ngAfterViewInit(): void {
        this.initializeApollonEditor();
        if (this.resizeOptions) {
            if (this.resizeOptions.initialWidth) {
                this.renderer.setStyle(this.resizeContainer.nativeElement, 'width', this.resizeOptions.initialWidth);
            }
            interact('.resizable')
                .resizable({
                    edges: { left: false, right: '.draggable-right', bottom: false, top: false },
                    restrictSize: {
                        min: { width: 15 },
                        max: { width: this.resizeOptions.maxWidth ? this.resizeOptions.maxWidth : 2500 },
                    },
                    inertia: true,
                })
                .on('resizestart', function(event: any) {
                    event.target.classList.add('card-resizable');
                })
                .on('resizeend', function(event: any) {
                    event.target.classList.remove('card-resizable');
                })
                .on('resizemove', (event: any) => {
                    const target = event.target;
                    target.style.width = event.rect.width + 'px';
                });
        }
    }

    /**
     * This function initializes the Apollon editor in Modeling mode.
     */
    private initializeApollonEditor(): void {
        if (this.apollonEditor !== null) {
            this.apollonEditor.destroy();
        }
        // Apollon doesn't need assessments in Modeling mode
        this.removeAssessments(this.umlModel);
        this.apollonEditor = new ApollonEditor(this.editorContainer.nativeElement, {
            model: this.umlModel,
            mode: ApollonMode.Modelling,
            readonly: this.readOnly,
            type: this.diagramType,
        });
    }

    /**
     * Removes the Assessments from a given UMLModel. In modeling mode the assessments are not needed.
     * Also they should not be sent to the server and persisted as part of the model JSON.
     *
     * @param umlModel the model for which the assessments should be removed
     */
    private removeAssessments(umlModel: UMLModel): void {
        if (umlModel) {
            umlModel.assessments = [];
        }
    }

    /**
     * Returns the current model of the Apollon editor. It removes the assessment first, as it should not be part
     * of the model outside of Apollon.
     */
    getCurrentModel(): UMLModel {
        const currentModel: UMLModel = this.apollonEditor!.model;
        this.removeAssessments(currentModel);
        return currentModel;
    }

    /**
     * This function opens the modal for the help dialog.
     */
    open(content: any): void {
        this.modalService.open(content, { size: 'lg' });
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.umlModel && changes.umlModel.currentValue && this.apollonEditor) {
            this.umlModel = changes.umlModel.currentValue;
            // Apollon doesn't need assessments in Modeling mode
            this.removeAssessments(this.umlModel);
            this.apollonEditor.model = this.umlModel;
        } else if (changes.diagramType && changes.diagramType.currentValue && this.apollonEditor) {
            this.umlModel = undefined;
            this.initializeApollonEditor();
        }
    }

    ngOnDestroy(): void {
        if (this.apollonEditor !== null) {
            this.apollonEditor.destroy();
        }
    }
}
