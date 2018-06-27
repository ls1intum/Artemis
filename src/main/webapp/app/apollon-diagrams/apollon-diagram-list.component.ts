import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService } from 'ng-jhipster';
import * as ApollonDiagramTitleFormatter from './apollonDiagramTitleFormatter';
import { ApollonDiagram, ApollonDiagramService } from '../entities/apollon-diagram';
import { ApollonDiagramCreateFormComponent } from './apollon-diagram-create-form.component';

@Component({
    selector: 'jhi-apollon-diagram-list',
    templateUrl: './apollon-diagram-list.component.html',
    providers: [ApollonDiagramService, JhiAlertService]
})
export class ApollonDiagramListComponent implements OnInit {
    apollonDiagrams: ApollonDiagram[] = [];

    constructor(
        private apollonDiagramsService: ApollonDiagramService,
        private jhiAlertService: JhiAlertService,
        private modalService: NgbModal,
        private route: ActivatedRoute,
        private router: Router
    ) {}

    ngOnInit() {
        this.apollonDiagramsService.query().subscribe(
            response => {
                this.apollonDiagrams = response.body;
            },
            response => {
                this.jhiAlertService.error('Error while loading Apollon diagrams');
            }
        );
    }

    goToDetailsPage(id: number) {
        this.router.navigate([id], {
            relativeTo: this.route
        });
    }

    getTitleForApollonDiagram(diagram: ApollonDiagram) {
        return ApollonDiagramTitleFormatter.getTitle(diagram);
    }

    openCreateDiagramDialog() {
        const modalRef = this.modalService.open(ApollonDiagramCreateFormComponent, { size: 'lg', backdrop: 'static' });
        const formComponentInstance = modalRef.componentInstance as ApollonDiagramCreateFormComponent;
        formComponentInstance.apollonDiagram = { title: '' };
    }
}
