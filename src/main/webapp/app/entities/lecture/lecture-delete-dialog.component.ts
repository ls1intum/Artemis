import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { LectureService } from './lecture.service';
import { Lecture } from 'app/entities/lecture/lecture.model';

@Component({
    selector: 'jhi-lecture-delete-dialog',
    templateUrl: './lecture-delete-dialog.component.html'
})
export class LectureDeleteDialogComponent {
    lecture: Lecture;

    constructor(protected lectureService: LectureService, public activeModal: NgbActiveModal, protected eventManager: JhiEventManager) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.lectureService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'lectureListModification',
                content: 'Deleted an lecture'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-lecture-delete-popup',
    template: ''
})
export class LectureDeletePopupComponent implements OnInit, OnDestroy {
    protected ngbModalRef: NgbModalRef;

    constructor(protected activatedRoute: ActivatedRoute, protected router: Router, protected modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ lecture }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(LectureDeleteDialogComponent as Component, { size: 'lg', backdrop: 'static' });
                this.ngbModalRef.componentInstance.lecture = lecture;
                this.ngbModalRef.result.then(
                    result => {
                        this.router.navigate(['/lecture', { outlets: { popup: null } }]);
                        this.ngbModalRef = null;
                    },
                    reason => {
                        this.router.navigate(['/lecture', { outlets: { popup: null } }]);
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
