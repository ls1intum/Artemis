import { AfterViewInit, Component, ElementRef, Input, OnChanges, OnDestroy, Renderer2, SimpleChanges, ViewChild } from '@angular/core';
import { ApollonEditor, ApollonMode, UMLDiagramType, UMLElementType, UMLModel, UMLRelationship, UMLRelationshipType } from '@ls1intum/apollon';
import { JhiAlertService } from 'ng-jhipster';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import interact from 'interactjs';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { associationUML, personUML, studentUML } from 'app/guided-tour/guided-tour-task.model';

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
    diagramType: UMLDiagramType;
    @Input()
    readOnly = false;
    @Input()
    resizeOptions: { initialWidth: string; maxWidth?: number };

    private apollonEditor: ApollonEditor | null = null;

    constructor(private jhiAlertService: JhiAlertService, private renderer: Renderer2, private modalService: NgbModal, private guidedTourService: GuidedTourService) {}

    ngAfterViewInit(): void {
        this.initializeApollonEditor();
        this.guidedTourService.checkModelingComponent().subscribe(key => {
            if (key) {
                this.assessModelForGuidedTour(key, this.getCurrentModel());
            }
        });
        if (this.resizeOptions) {
            if (this.resizeOptions.initialWidth) {
                this.renderer.setStyle(this.resizeContainer.nativeElement, 'width', this.resizeOptions.initialWidth);
            }
            interact('.resizable')
                .resizable({
                    edges: { left: false, right: '.draggable-right', bottom: false, top: false },
                    modifiers: [
                        interact.modifiers!.restrictSize({
                            min: { width: 15, height: 0 },
                            max: { width: this.resizeOptions.maxWidth ? this.resizeOptions.maxWidth : 2500, height: 2000 },
                        }),
                    ],
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
        }
    }

    ngOnDestroy(): void {
        if (this.apollonEditor !== null) {
            this.apollonEditor.destroy();
        }
    }

    /**
     * Assess the model for the modeling guided tutorial
     * @param umlName  the identifier of the UML element that has to be assessed
     * @param umlModel  the current UML model in the editor
     */
    assessModelForGuidedTour(umlName: string, umlModel: UMLModel): void {
        // Find the required UML classes
        const personClass = this.elementWithClass(personUML.name, umlModel);
        const studentClass = this.elementWithClass(studentUML.name, umlModel);
        let personStudentAssociation: UMLRelationship | undefined;

        switch (umlName) {
            // Check if the Person class is correct
            case personUML.name: {
                const nameAttribute = this.elementWithAttribute(personUML.attribute, umlModel);
                const personClassCorrect = personClass && nameAttribute ? nameAttribute.owner === personClass.id : false;
                this.guidedTourService.updateModelingResult(umlName, personClassCorrect);
                break;
            }
            // Check if the Student class is correct
            case studentUML.name: {
                const majorAttribute = this.elementWithAttribute(studentUML.attribute, umlModel);
                const visitLectureMethod = this.elementWithMethod(studentUML.method, umlModel);
                const studentClassCorrect =
                    studentClass && majorAttribute && visitLectureMethod ? majorAttribute.owner === studentClass.id && visitLectureMethod.owner === studentClass.id : false;
                this.guidedTourService.updateModelingResult(umlName, studentClassCorrect);
                break;
            }
            // Check if the Inheritance association is correct
            case associationUML.name: {
                personStudentAssociation = umlModel.relationships.find(
                    relationship =>
                        relationship.source.element === studentClass!.id &&
                        relationship.target.element === personClass!.id &&
                        relationship.type === UMLRelationshipType.ClassInheritance,
                );
                this.guidedTourService.updateModelingResult(umlName, !!personStudentAssociation);
                break;
            }
        }
    }

    /**
     * Return the UMLModelElement of the type class with the @param name
     * @param name class name
     * @param umlModel current model that is assessed
     */
    elementWithClass(name: string, umlModel: UMLModel) {
        return umlModel.elements.find(element => element.name.trim() === name && element.type === UMLElementType.Class);
    }

    /**
     * Return the UMLModelElement of the type ClassAttribute with the @param attribute
     * @param attribute name
     * @param umlModel current model that is assessed
     */
    elementWithAttribute(attribute: string, umlModel: UMLModel) {
        return umlModel.elements.find(element => element.name.includes(attribute) && element.type === UMLElementType.ClassAttribute);
    }

    /**
     * Return the UMLModelElement of the type ClassMethod with the @param method
     * @param method name
     * @param umlModel current model that is assessed
     */
    elementWithMethod(method: string, umlModel: UMLModel) {
        return umlModel.elements.find(element => element.name.includes(method) && element.type === UMLElementType.ClassMethod);
    }
}
