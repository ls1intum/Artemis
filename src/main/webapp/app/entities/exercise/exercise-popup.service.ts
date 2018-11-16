import { Component, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { Exercise } from './exercise.model';
import { ExerciseLtiConfigurationService, ExerciseService } from './exercise.service';
import { LtiConfiguration } from 'app/entities/lti-configuration';

@Injectable({ providedIn: 'root' })
export class ExercisePopupService {
    private ngbModalRef: NgbModalRef;

    constructor(
        private modalService: NgbModal,
        private router: Router,
        private exerciseService: ExerciseService,
        private exerciseLtiConfigurationService: ExerciseLtiConfigurationService
    ) {
        this.ngbModalRef = null;
    }

    open(component: Component, id?: number | any, lti?: boolean | any): Promise<NgbModalRef> {
        return new Promise<NgbModalRef>((resolve, reject) => {
            const isOpen = this.ngbModalRef !== null;
            if (isOpen) {
                resolve(this.ngbModalRef);
            }

            if (id) {
                this.exerciseService.find(id).subscribe((exerciseResponse: HttpResponse<Exercise>) => {
                    if (lti) {
                        this.exerciseLtiConfigurationService.find(id).subscribe((ltiConfigurationResponse: HttpResponse<any>) => {
                            const exercise: Exercise = exerciseResponse.body;
                            const ltiConfiguration: LtiConfiguration = ltiConfigurationResponse.body;
                            this.ngbModalRef = this.exerciseModalRef(component, exercise, ltiConfiguration);
                            resolve(this.ngbModalRef);
                        });
                    } else {
                        const exercise: Exercise = exerciseResponse.body;
                        this.ngbModalRef = this.exerciseModalRef(component, exercise, null);
                        resolve(this.ngbModalRef);
                    }
                });
            }
        });
    }

    exerciseModalRef(component: Component, exercise: Exercise, ltiConfiguration: LtiConfiguration): NgbModalRef {
        const modalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exercise = exercise;
        modalRef.componentInstance.ltiConfiguration = ltiConfiguration;
        modalRef.result.then(
            result => {
                this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
                this.ngbModalRef = null;
            },
            reason => {
                this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
                this.ngbModalRef = null;
            }
        );
        return modalRef;
    }
}
