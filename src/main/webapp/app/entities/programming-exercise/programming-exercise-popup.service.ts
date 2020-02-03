import { Component, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { ProgrammingExercise } from './programming-exercise.model';
import { ProgrammingExerciseService } from './services/programming-exercise.service';
import { DatePipe } from '@angular/common';
import { CourseService } from 'app/entities/course/course.service';

@Injectable({ providedIn: 'root' })
export class ProgrammingExercisePopupService {
    private ngbModalRef: NgbModalRef | null;

    constructor(
        private datePipe: DatePipe,
        private modalService: NgbModal,
        private router: Router,
        private programmingExerciseService: ProgrammingExerciseService,
        private courseService: CourseService,
    ) {
        this.ngbModalRef = null;
    }

    open(component: Component, id?: number | any, courseId?: number): Promise<NgbModalRef> {
        return new Promise<NgbModalRef>((resolve, reject) => {
            if (this.ngbModalRef != null) {
                resolve(this.ngbModalRef);
            }

            if (id) {
                this.programmingExerciseService.find(id).subscribe((programmingExerciseResponse: HttpResponse<ProgrammingExercise>) => {
                    const programmingExercise: ProgrammingExercise = programmingExerciseResponse.body!;
                    this.ngbModalRef = this.programmingExerciseModalRef(component, programmingExercise);
                    resolve(this.ngbModalRef);
                });
            } else {
                // setTimeout used as a workaround for getting ExpressionChangedAfterItHasBeenCheckedError
                setTimeout(() => {
                    if (courseId) {
                        this.courseService.find(courseId).subscribe(res => {
                            const course = res.body!;
                            this.ngbModalRef = this.programmingExerciseModalRef(component, new ProgrammingExercise(course));
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
        const modalRef: NgbModalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.programmingExercise = programmingExercise;
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
