import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { ApollonDiagram, ApollonDiagramService } from '../entities/apollon-diagram';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import ApollonEditor from '@ls1intum/apollon';
import { ActivatedRoute } from '@angular/router';
import * as ApollonDiagramTitleFormatter from './apollonDiagramTitleFormatter';

@Component({
    selector: 'jhi-apollon-diagram-student',
    templateUrl: './apollon-diagram-student.component.html',
    providers: []
})
export class ApollonDiagramStudentComponent implements OnInit, OnDestroy {
    @ViewChild('editorContainer') editorContainer: ElementRef;

    diagramTitle = '';
    diagram: ApollonDiagram | null = null;
    apollonEditor: ApollonEditor | null = null;
    submitted;

    constructor(
        private apollonDiagramService: ApollonDiagramService,
        private jhiAlertService: JhiAlertService,
        private modalService: NgbModal,
        private route: ActivatedRoute
    ) {
        this.submitted = false;
    }

    ngOnInit() {
        this.route.params.subscribe(params => {
            const id = Number(params['id']);

            this.apollonDiagramService.find(id).subscribe(
                response => {
                    const diagram = response.body;

                    this.diagram = diagram;
                    this.diagramTitle = ApollonDiagramTitleFormatter.getTitle(diagram);

                    const state = JSON.parse(diagram.jsonRepresentation);
                    this.initializeApollonEditor(state);
                },
                response => {
                    this.jhiAlertService.error('Error while loading Apollon diagram');
                }
            );
        });
    }

    ngOnDestroy() {
        if (this.apollonEditor !== null) {
            this.apollonEditor.destroy();
        }
    }

    initializeApollonEditor(initialState) {
        if (this.apollonEditor !== null) {
            this.apollonEditor.destroy();
        }

        // TODO: disable interactive mode
        this.apollonEditor = new ApollonEditor(this.editorContainer.nativeElement, {
            initialState,
            mode: 'MODELING_ONLY',
            diagramType: 'CLASS'
        });
    }

    saveDiagram() {
        if (this.diagram === null) {
            // Should never happen, but let's be defensive anyway
            return;
        }

        const diagramState = this.apollonEditor.getState();
        const updatedDiagram: ApollonDiagram = {
            ...this.diagram,
            jsonRepresentation: JSON.stringify(diagramState)
        };

        this.apollonDiagramService.update(updatedDiagram).subscribe(
            () => {},
            response => {
                this.jhiAlertService.error('Error while updating Apollon diagram');
            }
        );
    }

    submit() {
        // TODO: handle submit case. set diagram to readonly
    }
}
