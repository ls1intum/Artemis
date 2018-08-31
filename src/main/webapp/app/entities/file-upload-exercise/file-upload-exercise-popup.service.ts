import { Component, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { FileUploadExercise } from './file-upload-exercise.model';
import { FileUploadExerciseService } from './file-upload-exercise.service';
import { DatePipe } from '@angular/common';
import { CourseService } from '../course';

@Injectable()
export class FileUploadExercisePopupService {
    private ngbModalRef: NgbModalRef;

    constructor(
        private datePipe: DatePipe,
        private modalService: NgbModal,
        private router: Router,
        private fileUploadExerciseService: FileUploadExerciseService,
        private courseService: CourseService

    ) {
        this.ngbModalRef = null;
    }

    open(component: Component, id?: number | any, courseId?: number): Promise<NgbModalRef> {
        return new Promise<NgbModalRef>((resolve, reject) => {
            const isOpen = this.ngbModalRef !== null;
            if (isOpen) {
                resolve(this.ngbModalRef);
            }

            if (id) {
                this.fileUploadExerciseService.find(id)
                    .subscribe((fileUploadExerciseResponse: HttpResponse<FileUploadExercise>) => {
                        const fileUploadExercise: FileUploadExercise = fileUploadExerciseResponse.body;
                        fileUploadExercise.releaseDate = this.datePipe.transform(fileUploadExercise.releaseDate, 'yyyy-MM-ddTHH:mm:ss');
                        fileUploadExercise.dueDate = this.datePipe.transform(fileUploadExercise.dueDate, 'yyyy-MM-ddTHH:mm:ss');
                        this.ngbModalRef = this.fileUploadExerciseModalRef(component, fileUploadExercise);
                        resolve(this.ngbModalRef);
                    });
            } else {
                // setTimeout used as a workaround for getting ExpressionChangedAfterItHasBeenCheckedError
                setTimeout(() => {
                    if (courseId) {
                        this.courseService.find(courseId).subscribe(res => {
                            const course = res.body;
                            this.ngbModalRef = this.fileUploadExerciseModalRef(component, new FileUploadExercise(course));
                            resolve(this.ngbModalRef);
                        });
                    } else {
                        this.ngbModalRef = this.fileUploadExerciseModalRef(component, new FileUploadExercise());
                        resolve(this.ngbModalRef);
                    }
                }, 0);
            }
        });
    }

    fileUploadExerciseModalRef(component: Component, fileUploadExercise: FileUploadExercise): NgbModalRef {
        const modalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static'});
        modalRef.componentInstance.fileUploadExercise = fileUploadExercise;
        modalRef.result.then(result => {
            this.router.navigate([{ outlets: { popup: null }}], { replaceUrl: true, queryParamsHandling: 'merge' });
            this.ngbModalRef = null;
        }, reason => {
            this.router.navigate([{ outlets: { popup: null }}], { replaceUrl: true, queryParamsHandling: 'merge' });
            this.ngbModalRef = null;
        });
        return modalRef;
    }
}
