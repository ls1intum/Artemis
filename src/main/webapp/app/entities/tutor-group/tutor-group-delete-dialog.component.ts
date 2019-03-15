import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { TutorGroupService } from './tutor-group.service';
import { TutorGroup } from 'app/entities/tutor-group';

@Component({
    selector: 'jhi-tutor-group-delete-dialog',
    templateUrl: './tutor-group-delete-dialog.component.html'
})
export class TutorGroupDeleteDialogComponent {
    tutorGroup: TutorGroup;

    constructor(
        protected tutorGroupService: TutorGroupService,
        public activeModal: NgbActiveModal,
        protected eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.tutorGroupService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'tutorGroupListModification',
                content: 'Deleted an tutorGroup'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-tutor-group-delete-popup',
    template: ''
})
export class TutorGroupDeletePopupComponent implements OnInit, OnDestroy {
    protected ngbModalRef: NgbModalRef;

    constructor(protected activatedRoute: ActivatedRoute, protected router: Router, protected modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ tutorGroup }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(TutorGroupDeleteDialogComponent as Component, { size: 'lg', backdrop: 'static' });
                this.ngbModalRef.componentInstance.tutorGroup = tutorGroup;
                this.ngbModalRef.result.then(
                    result => {
                        this.router.navigate(['/tutor-group', { outlets: { popup: null } }]);
                        this.ngbModalRef = null;
                    },
                    reason => {
                        this.router.navigate(['/tutor-group', { outlets: { popup: null } }]);
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
