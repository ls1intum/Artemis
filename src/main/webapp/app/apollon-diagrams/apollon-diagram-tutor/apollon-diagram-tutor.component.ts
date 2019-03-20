import { Component, ElementRef, OnInit, ViewChild, Input, OnDestroy, AfterViewInit } from '@angular/core';
import ApollonEditor, { ApollonOptions, Point, State } from '@ls1intum/apollon';
import * as $ from 'jquery';
import { DiagramType } from 'app/entities/modeling-exercise';
import { JhiAlertService } from 'ng-jhipster';
import * as interact from 'interactjs';

@Component({
    selector: 'jhi-apollon-diagram-tutor',
    templateUrl: './apollon-diagram-tutor.component.html',
    styleUrls: ['./apollon-diagram-tutor.component.scss']
})
export class ApollonDiagramTutorComponent implements OnInit, OnDestroy, AfterViewInit {
    @ViewChild('editorContainer') editorContainer: ElementRef;
    @Input() diagramType: DiagramType;
    @Input() model: string;
    @Input() resizable = false;
    apollonEditor: ApollonEditor;

    constructor(private jhiAlertService: JhiAlertService) {}

    ngOnInit() {

    }
    ngOnDestroy(): void {
        if (this.apollonEditor) {
            this.apollonEditor.destroy();
        }
    }
    ngAfterViewInit(): void {
        if (this.model) {
            this.initializeApollonEditor(JSON.parse(this.model));
        } else {
            this.jhiAlertService.error('arTeMiSApp.apollonDiagram.submission.noModel');
        }
        if (this.resizable) {
            interact('.resizable')
                .resizable({
                    edges: { left: false, right: '.draggable-right', bottom: false, top: false },
                    restrictSize: {
                        min: { width: 15 },
                        max: { width: 600 }
                    },
                    inertia: true
                })
                .on('resizemove', event => {
                    const target = event.target;
                    target.style.width = event.rect.width + 'px';
                });
        }
    }

    /**
     * Initializes the Apollon editor in read only mode.
     */
    initializeApollonEditor(initialState: State) {
        if (this.apollonEditor) {
            this.apollonEditor.destroy();
        }
        this.apollonEditor = new ApollonEditor(this.editorContainer.nativeElement, {
            initialState,
            mode: 'READ_ONLY',
            diagramType: <ApollonOptions['diagramType']>this.diagramType
        });
        //
        // this.apollonEditor.subscribeToSelectionChange(selection => {
        //     const selectedEntities: string[] = [];
        //     for (const entity of selection.entityIds) {
        //         selectedEntities.push(entity);
        //     }
        //     this.selectedEntities = selectedEntities;
        //     const selectedRelationships: string[] = [];
        //     for (const rel of selection.relationshipIds) {
        //         selectedRelationships.push(rel);
        //     }
        //     this.selectedRelationships = selectedRelationships;
        // });

        // this.initializeAssessments();
    }
}
