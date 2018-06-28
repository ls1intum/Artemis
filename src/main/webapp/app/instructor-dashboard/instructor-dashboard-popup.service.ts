import { Component, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Exercise, ExerciseService } from '../entities/exercise';
import { Participation, ParticipationService } from '../entities/participation';

@Injectable()
export class InstructorDashboardPopupService {
    private ngbModalRef: NgbModalRef;

    constructor(
        private datePipe: DatePipe,
        private modalService: NgbModal,
        private router: Router,
        private exerciseService: ExerciseService,
        private participationService: ParticipationService

    ) {
        this.ngbModalRef = null;
    }

    open(component: Component, id: number | any, exercisePopup: boolean): Promise<NgbModalRef> {
        return new Promise<NgbModalRef>((resolve, reject) => {
            const isOpen = this.ngbModalRef !== null;
            if (isOpen) {
                resolve(this.ngbModalRef);
            }

            if (id) {
                if (exercisePopup) {
                    this.exerciseService.find(id)
                        .subscribe((res: HttpResponse<Exercise>) => {
                            const exercise: Exercise = res.body;
                            this.ngbModalRef = this.exerciseModalRef(component, exercise);
                            resolve(this.ngbModalRef);
                        });
                } else {
                    this.participationService.find(id).subscribe(res => {
                        const participation: Participation = res.body;
                        this.ngbModalRef = this.resultModalRef(component, participation);
                        resolve(this.ngbModalRef);
                    });
                }
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
        modalRef.result.then(result => {
            this.router.navigate([{ outlets: { popup: null }}], { replaceUrl: true, queryParamsHandling: 'merge' });
            this.ngbModalRef = null;
        }, reason => {
            this.router.navigate([{ outlets: { popup: null }}], { replaceUrl: true, queryParamsHandling: 'merge' });
            this.ngbModalRef = null;
        });
        return modalRef;
    }

    resultModalRef(component: Component, participation: Participation): NgbModalRef {
        const modalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static'});
        modalRef.componentInstance.participation = participation;
        modalRef.componentInstance.result = {
            resultString: null,
            completionDate: new Date(),
            successful: true,
            score: 100,
            id: null
        };
        modalRef.result.then(result => {
            // $state.go('instructor-dashboard', $state.params, {reload: true});
            this.router.navigate([{ outlets: { popup: null }}], { replaceUrl: true, queryParamsHandling: 'merge' });
            this.ngbModalRef = null;
        }, reason => {
            // $state.go('instructor-dashboard', $state.params);
            this.router.navigate([{ outlets: { popup: null }}], { replaceUrl: true, queryParamsHandling: 'merge' });
            this.ngbModalRef = null;
        });
        return modalRef;
    }
}
