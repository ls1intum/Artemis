import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { FileUploadExercise } from './file-upload-exercise.model';
import { FileUploadExercisePopupService } from './file-upload-exercise-popup.service';
import { FileUploadExerciseService } from './file-upload-exercise.service';

@Component({
    selector: 'jhi-file-upload-exercise-delete-dialog',
    templateUrl: './file-upload-exercise-delete-dialog.component.html'
})
export class FileUploadExerciseDeleteDialogComponent {

    fileUploadExercise: FileUploadExercise;

    constructor(
        private fileUploadExerciseService: FileUploadExerciseService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.fileUploadExerciseService.delete(id).subscribe((response) => {
            this.eventManager.broadcast({
                name: 'fileUploadExerciseListModification',
                content: 'Deleted an fileUploadExercise'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-file-upload-exercise-delete-popup',
    template: ''
})
export class FileUploadExerciseDeletePopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private fileUploadExercisePopupService: FileUploadExercisePopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            this.fileUploadExercisePopupService
                .open(FileUploadExerciseDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
