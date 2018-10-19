import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IExerciseResult } from 'app/shared/model/exercise-result.model';
import { ExerciseResultService } from './exercise-result.service';

@Component({
    selector: 'jhi-exercise-result-delete-dialog',
    templateUrl: './exercise-result-delete-dialog.component.html'
})
export class ExerciseResultDeleteDialogComponent {
    exerciseResult: IExerciseResult;

    constructor(
        private exerciseResultService: ExerciseResultService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.exerciseResultService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'exerciseResultListModification',
                content: 'Deleted an exerciseResult'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-exercise-result-delete-popup',
    template: ''
})
export class ExerciseResultDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ exerciseResult }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(ExerciseResultDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.exerciseResult = exerciseResult;
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
