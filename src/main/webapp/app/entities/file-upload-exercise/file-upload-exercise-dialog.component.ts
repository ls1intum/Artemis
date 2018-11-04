import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { FileUploadExercise } from './file-upload-exercise.model';
import { FileUploadExercisePopupService } from './file-upload-exercise-popup.service';
import { FileUploadExerciseService } from './file-upload-exercise.service';
import { Course, CourseService } from '../course';

import { Subscription } from 'rxjs/Subscription';

@Component({
    selector: 'jhi-file-upload-exercise-dialog',
    templateUrl: './file-upload-exercise-dialog.component.html'
})
export class FileUploadExerciseDialogComponent implements OnInit {
    fileUploadExercise: FileUploadExercise;
    isSaving: boolean;
    maxScorePattern = '^[1-9]{1}[0-9]{0,4}$'; // make sure max score is a positive natural integer and not too large

    courses: Course[];

    constructor(
        public activeModal: NgbActiveModal,
        private jhiAlertService: JhiAlertService,
        private fileUploadExerciseService: FileUploadExerciseService,
        private courseService: CourseService,
        private eventManager: JhiEventManager
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.courseService.query().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res)
        );
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    save() {
        this.isSaving = true;
        if (this.fileUploadExercise.id !== undefined) {
            this.subscribeToSaveResponse(this.fileUploadExerciseService.update(this.fileUploadExercise));
        } else {
            this.subscribeToSaveResponse(this.fileUploadExerciseService.create(this.fileUploadExercise));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<FileUploadExercise>>) {
        result.subscribe(
            (res: HttpResponse<FileUploadExercise>) => this.onSaveSuccess(res.body),
            (res: HttpErrorResponse) => this.onSaveError()
        );
    }

    private onSaveSuccess(result: FileUploadExercise) {
        this.eventManager.broadcast({ name: 'fileUploadExerciseListModification', content: 'OK' });
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError() {
        this.isSaving = false;
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message, null, null);
    }

    trackCourseById(index: number, item: Course) {
        return item.id;
    }
}

@Component({
    selector: 'jhi-file-upload-exercise-popup',
    template: ''
})
export class FileUploadExercisePopupComponent implements OnInit, OnDestroy {
    routeSub: Subscription;

    constructor(private route: ActivatedRoute, private fileUploadExercisePopupService: FileUploadExercisePopupService) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe(params => {
            if (params['id']) {
                this.fileUploadExercisePopupService.open(FileUploadExerciseDialogComponent as Component, params['id']);
            } else {
                if (params['courseId']) {
                    this.fileUploadExercisePopupService.open(FileUploadExerciseDialogComponent as Component, undefined, params['courseId']);
                } else {
                    this.fileUploadExercisePopupService.open(FileUploadExerciseDialogComponent as Component);
                }
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
