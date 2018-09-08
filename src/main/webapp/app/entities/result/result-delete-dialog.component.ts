import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IResult } from 'app/shared/model/result.model';
import { ResultService } from './result.service';

@Component({
    selector: 'jhi-result-delete-dialog',
    templateUrl: './result-delete-dialog.component.html'
})
export class ResultDeleteDialogComponent {
    result: IResult;

    constructor(private resultService: ResultService, public activeModal: NgbActiveModal, private eventManager: JhiEventManager) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.resultService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'resultListModification',
                content: 'Deleted an result'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-result-delete-popup',
    template: ''
})
export class ResultDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ result }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(ResultDeleteDialogComponent as Component, { size: 'lg', backdrop: 'static' });
                this.ngbModalRef.componentInstance.result = result;
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
