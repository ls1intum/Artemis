import { AfterViewInit, Component, ElementRef, Input, OnDestroy, Renderer2, ViewChild, OnChanges, SimpleChanges } from '@angular/core';
import { ApollonEditor, ApollonMode, DiagramType, UMLModel } from '@ls1intum/apollon';
import { JhiAlertService } from 'ng-jhipster';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import * as interact from 'interactjs';

@Component({
    selector: 'jhi-modeling-editor',
    templateUrl: './modeling-editor.component.html',
    styleUrls: ['./modeling-editor.component.scss'],
})
export class ModelingEditorComponent implements AfterViewInit, OnDestroy, OnChanges {
    @ViewChild('editorContainer')
    editorContainer: ElementRef;
    @ViewChild('resizeContainer')
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
        this.apollonEditor = new ApollonEditor(this.editorContainer.nativeElement, {
            model: this.umlModel,
            mode: ApollonMode.Modelling,
            readonly: this.readOnly,
            type: this.diagramType,
        });
    }

    /**
     * Returns the current model of the Apollon editor.
     */
    getCurrentModel(): UMLModel {
        return this.apollonEditor.model;
    }

    /**
     * This function opens the modal for the help dialog.
     */
    open(content: any): void {
        this.modalService.open(content, { size: 'lg' });
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.umlModel && changes.umlModel.currentValue && this.apollonEditor) {
            this.apollonEditor.model = changes.umlModel.currentValue;
        }
    }

    ngOnDestroy(): void {
        if (this.apollonEditor !== null) {
            this.apollonEditor.destroy();
        }
    }
}
