import { Injectable, Component } from '@angular/core';
import { Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Exercise } from './exercise.model';
import { ExerciseService } from './exercise.service';

@Injectable()
export class ExercisePopupService {
    private ngbModalRef: NgbModalRef;

    constructor(
        private datePipe: DatePipe,
        private modalService: NgbModal,
        private router: Router,
        private exerciseService: ExerciseService

    ) {
        this.ngbModalRef = null;
    }

    open(component: Component, id?: number | any): Promise<NgbModalRef> {
        return new Promise<NgbModalRef>((resolve, reject) => {
            const isOpen = this.ngbModalRef !== null;
            if (isOpen) {
                resolve(this.ngbModalRef);
            }

            if (id) {
                this.exerciseService.find(id)
                    .subscribe((exerciseResponse: HttpResponse<Exercise>) => {
                        const exercise: Exercise = exerciseResponse.body;
                        exercise.releaseDate = this.datePipe
                            .transform(exercise.releaseDate, 'yyyy-MM-ddTHH:mm:ss');
                        exercise.dueDate = this.datePipe
                            .transform(exercise.dueDate, 'yyyy-MM-ddTHH:mm:ss');
                        this.ngbModalRef = this.exerciseModalRef(component, exercise);
                        resolve(this.ngbModalRef);
                    });
            } else {
                // setTimeout used as a workaround for getting ExpressionChangedAfterItHasBeenCheckedError
                setTimeout(() => {
                    this.ngbModalRef = this.exerciseModalRef(component, new Exercise());
                    resolve(this.ngbModalRef);
                }, 0);
            }
        });
    }

    exerciseModalRef(component: Component, exercise: Exercise): NgbModalRef {
        const modalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static'});
        modalRef.componentInstance.exercise = exercise;
        modalRef.result.then((result) => {
            this.router.navigate([{ outlets: { popup: null }}], { replaceUrl: true, queryParamsHandling: 'merge' });
            this.ngbModalRef = null;
        }, (reason) => {
            this.router.navigate([{ outlets: { popup: null }}], { replaceUrl: true, queryParamsHandling: 'merge' });
            this.ngbModalRef = null;
        });
        return modalRef;
    }
}
