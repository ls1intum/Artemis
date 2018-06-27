import { Component, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { ProgrammingExercise } from './programming-exercise.model';
import { ProgrammingExerciseService } from './programming-exercise.service';
import { DatePipe } from '@angular/common';
import { CourseService } from '../course';

@Injectable()
export class ProgrammingExercisePopupService {
    private ngbModalRef: NgbModalRef;

    constructor(
        private datePipe: DatePipe,
        private modalService: NgbModal,
        private router: Router,
        private programmingExerciseService: ProgrammingExerciseService,
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
                this.programmingExerciseService.find(id)
          .subscribe((programmingExerciseResponse: HttpResponse<ProgrammingExercise>) => {
          const programmingExercise: ProgrammingExercise = programmingExerciseResponse.body;
            programmingExercise.releaseDate = this.datePipe
                        .transform(programmingExercise.releaseDate, 'yyyy-MM-ddTHH:mm:ss');
                    programmingExercise.dueDate = this.datePipe
                        .transform(programmingExercise.dueDate, 'yyyy-MM-ddTHH:mm:ss');
                    this.ngbModalRef = this.programmingExerciseModalRef(component, programmingExercise);
                    resolve(this.ngbModalRef);
                });
            } else {
                // setTimeout used as a workaround for getting ExpressionChangedAfterItHasBeenCheckedError
                setTimeout(() => {
                    if (courseId) {
                        this.courseService.find(courseId).subscribe(res => {
                            this.ngbModalRef = this.programmingExerciseModalRef(component,
                                new ProgrammingExercise(undefined, undefined, undefined, undefined,
                                    undefined, undefined, undefined, undefined, undefined, undefined, res.body));
                            resolve(this.ngbModalRef);
                        });
                    } else {
                        this.ngbModalRef = this.programmingExerciseModalRef(component, new ProgrammingExercise());
                        resolve(this.ngbModalRef);
                    }
                }, 0);
            }
        });
    }

    programmingExerciseModalRef(component: Component, programmingExercise: ProgrammingExercise): NgbModalRef {
        const modalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static'});
        modalRef.componentInstance.programmingExercise = programmingExercise;
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
