import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { ITextExercise } from 'app/shared/model/text-exercise.model';
import { TextExerciseService } from './text-exercise.service';

@Component({
    selector: 'jhi-text-exercise-delete-dialog',
    templateUrl: './text-exercise-delete-dialog.component.html'
})
export class TextExerciseDeleteDialogComponent {
    textExercise: ITextExercise;

    constructor(
        private textExerciseService: TextExerciseService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.textExerciseService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'textExerciseListModification',
                content: 'Deleted an textExercise'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-text-exercise-delete-popup',
    template: ''
})
export class TextExerciseDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ textExercise }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(TextExerciseDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.textExercise = textExercise;
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
