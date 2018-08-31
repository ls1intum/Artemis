import { Component, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { TextExercise } from './text-exercise.model';
import { TextExerciseService } from './text-exercise.service';
import { DatePipe } from '@angular/common';
import { CourseService } from '../course';

@Injectable()
export class TextExercisePopupService {
    private ngbModalRef: NgbModalRef;

    constructor(
        private datePipe: DatePipe,
        private modalService: NgbModal,
        private router: Router,
        private textExerciseService: TextExerciseService,
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
                this.textExerciseService.find(id)
                    .subscribe((textExerciseResponse: HttpResponse<TextExercise>) => {
                        const textExercise: TextExercise = textExerciseResponse.body;
                        textExercise.releaseDate = this.datePipe.transform(textExercise.releaseDate, 'yyyy-MM-ddTHH:mm:ss');
                        textExercise.dueDate = this.datePipe.transform(textExercise.dueDate, 'yyyy-MM-ddTHH:mm:ss');
                        this.ngbModalRef = this.textExerciseModalRef(component, textExercise);
                        resolve(this.ngbModalRef);
                    });
            } else {
                // setTimeout used as a workaround for getting ExpressionChangedAfterItHasBeenCheckedError
                setTimeout(() => {
                    if (courseId) {
                        this.courseService.find(courseId).subscribe(res => {
                            const course = res.body;
                            this.ngbModalRef = this.textExerciseModalRef(component, new TextExercise(course));
                            resolve(this.ngbModalRef);
                        });
                    } else {
                        this.ngbModalRef = this.textExerciseModalRef(component, new TextExercise());
                        resolve(this.ngbModalRef);
                    }
                }, 0);
            }
        });
    }

    textExerciseModalRef(component: Component, textExercise: TextExercise): NgbModalRef {
        const modalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static'});
        modalRef.componentInstance.textExercise = textExercise;
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
