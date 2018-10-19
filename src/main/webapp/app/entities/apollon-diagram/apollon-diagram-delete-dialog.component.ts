import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IApollonDiagram } from 'app/shared/model/apollon-diagram.model';
import { ApollonDiagramService } from './apollon-diagram.service';

@Component({
    selector: 'jhi-apollon-diagram-delete-dialog',
    templateUrl: './apollon-diagram-delete-dialog.component.html'
})
export class ApollonDiagramDeleteDialogComponent {
    apollonDiagram: IApollonDiagram;

    constructor(
        private apollonDiagramService: ApollonDiagramService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.apollonDiagramService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'apollonDiagramListModification',
                content: 'Deleted an apollonDiagram'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-apollon-diagram-delete-popup',
    template: ''
})
export class ApollonDiagramDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ apollonDiagram }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(ApollonDiagramDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.apollonDiagram = apollonDiagram;
                this.ngbModalRef.result.then(
                    result => {
                        this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
                        this.ngbModalRef = null;
                    },
                    reason => {
                        this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
                        this.ngbModalRef = null;
                    }
                );
            }, 0);
        });
    }

    ngOnDestroy() {
        this.ngbModalRef = null;
    }
}
