import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { ExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';
import { ExerciseHintService } from './exercise-hint.service';

@Component({
    selector: 'jhi-exercise-hint-delete-dialog',
    templateUrl: './exercise-hint-delete-dialog.component.html',
})
export class ExerciseHintDeleteDialogComponent {
    exerciseHint: ExerciseHint;

    constructor(protected exerciseHintService: ExerciseHintService, public activeModal: NgbActiveModal, protected eventManager: JhiEventManager) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.exerciseHintService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'exerciseHintListModification',
                content: 'Deleted an exerciseHint',
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-exercise-hint-delete-popup',
    template: '',
})
export class ExerciseHintDeletePopupComponent implements OnInit, OnDestroy {
    protected ngbModalRef: NgbModalRef | null;

    constructor(protected activatedRoute: ActivatedRoute, protected router: Router, protected modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ exerciseHint }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(ExerciseHintDeleteDialogComponent as Component, { size: 'lg', backdrop: 'static' });
                this.ngbModalRef.componentInstance.exerciseHint = exerciseHint;
                this.ngbModalRef.result.finally(() => {
                    this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
                    this.ngbModalRef = null;
                });
            }, 0);
        });
    }

    ngOnDestroy() {
        this.ngbModalRef = null;
    }
}
