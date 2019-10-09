import { Component, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { FileUploadExercise } from './file-upload-exercise.model';
import { FileUploadExerciseService } from './file-upload-exercise.service';
import { DatePipe } from '@angular/common';

@Injectable({ providedIn: 'root' })
export class FileUploadExercisePopupService {
    private ngbModalRef: NgbModalRef | null;

    constructor(private datePipe: DatePipe, private modalService: NgbModal, private router: Router, private fileUploadExerciseService: FileUploadExerciseService) {
        this.ngbModalRef = null;
    }

    /**
     * Opens a modal and resolves the data passed
     * @param component that will be created
     * @param exerciseId the id of the exercise
     */
    open(component: Component, exerciseId: number | any): Promise<NgbModalRef> {
        return new Promise<NgbModalRef>((resolve, reject) => {
            if (this.ngbModalRef != null) {
                resolve(this.ngbModalRef);
            }
            if (exerciseId) {
                this.fileUploadExerciseService.find(exerciseId).subscribe((fileUploadExerciseResponse: HttpResponse<FileUploadExercise>) => {
                    const fileUploadExercise: FileUploadExercise = fileUploadExerciseResponse.body!;
                    this.ngbModalRef = this.fileUploadExerciseModalRef(component, fileUploadExercise);
                    resolve(this.ngbModalRef);
                });
            }
        });
    }

    /**
     * Opens modal for file upload exercise
     * @param component which will be used for modal
     * @param fileUploadExercise for which modal will be opened
     */
    fileUploadExerciseModalRef(component: Component, fileUploadExercise: FileUploadExercise): NgbModalRef {
        const modalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.fileUploadExercise = fileUploadExercise;
        modalRef.result.then(
            result => {
                this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
                this.ngbModalRef = null;
            },
            reason => {
                this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
                this.ngbModalRef = null;
            },
        );
        return modalRef;
    }
}
