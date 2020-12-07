import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService } from 'ng-jhipster';
import { ApollonDiagramCreateFormComponent } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram-create-form.component';
import { ApollonDiagramService } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.service';
import { ApollonDiagram } from 'app/entities/apollon-diagram.model';
import { UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { SortService } from 'app/shared/service/sort.service';

@Component({
    selector: 'jhi-apollon-diagram-list',
    templateUrl: './apollon-diagram-list.component.html',
    providers: [ApollonDiagramService],
})
export class ApollonDiagramListComponent implements OnInit {
    apollonDiagrams: ApollonDiagram[] = [];
    predicate: string;
    reverse: boolean;
    courseId: number;

    constructor(
        private apollonDiagramsService: ApollonDiagramService,
        private jhiAlertService: JhiAlertService,
        private modalService: NgbModal,
        private sortService: SortService,
        private route: ActivatedRoute,
    ) {
        this.predicate = 'id';
        this.reverse = true;
    }

    /**
     * Initializes Apollon diagrams from the server
     */
    ngOnInit() {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.apollonDiagramsService.getDiagramsByCourse(this.courseId).subscribe(
            (response) => {
                this.apollonDiagrams = response.body!;
            },
            () => {
                this.jhiAlertService.error('artemisApp.apollonDiagram.home.error.loading');
            },
        );
    }

    /**
     * Deletes specified Apollon diagram
     * @param apollonDiagram
     */
    delete(apollonDiagram: ApollonDiagram) {
        this.apollonDiagramsService.delete(apollonDiagram.id!, this.courseId).subscribe(
            () => {
                this.jhiAlertService.success('artemisApp.apollonDiagram.delete.success', { title: apollonDiagram.title });
                this.apollonDiagrams = this.apollonDiagrams.filter((diagram) => {
                    return diagram.id !== apollonDiagram.id;
                });
            },
            () => {
                this.jhiAlertService.error('artemisApp.apollonDiagram.delete.error', { title: apollonDiagram.title });
            },
        );
    }

    /**
     * Returns the title for Apollon diagram
     * @param diagram
     */
    getTitleForApollonDiagram(diagram: ApollonDiagram): string {
        return diagram.title && diagram.title.trim().length ? diagram.title.trim() : `#${diagram.id}`;
    }

    /**
     * Opens dialog for creating a new diagram
     */
    openCreateDiagramDialog(courseId: number) {
        const modalRef = this.modalService.open(ApollonDiagramCreateFormComponent, { size: 'lg', backdrop: 'static' });
        const formComponentInstance = modalRef.componentInstance as ApollonDiagramCreateFormComponent;
        // class diagram is the default value and can be changed by the user in the creation dialog
        formComponentInstance.apollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, courseId);
    }

    /**
     * Returns the unique identifier for items in the collection
     * @param index of a diagram in the collection
     * @param item current diagram
     */
    trackId(index: number, item: ApollonDiagram) {
        return item.id;
    }

    sortRows() {
        this.sortService.sortByProperty(this.apollonDiagrams, this.predicate, this.reverse);
    }
}
