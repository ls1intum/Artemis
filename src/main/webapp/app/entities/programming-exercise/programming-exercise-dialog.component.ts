import { Component, OnDestroy, OnInit } from '@angular/core';

import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';

import * as moment from 'moment';
import { Observable } from 'rxjs/Observable';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { ProgrammingExercise } from './programming-exercise.model';
import { ProgrammingExercisePopupService } from './programming-exercise-popup.service';
import { ProgrammingExerciseService } from './programming-exercise.service';
import { Course, CourseService } from '../course';

@Component({
    selector: 'jhi-programming-exercise-dialog',
    templateUrl: './programming-exercise-dialog.component.html'
})
export class ProgrammingExerciseDialogComponent implements OnInit {
    programmingExercise: ProgrammingExercise;
    isSaving: boolean;
    releaseDate: Date;
    dueDate: Date;
    releaseClockToggled: boolean;
    dueClockToggled: boolean;

    courses: Course[];

    constructor(
        public activeModal: NgbActiveModal,
        private jhiAlertService: JhiAlertService,
        private programmingExerciseService: ProgrammingExerciseService,
        private courseService: CourseService,
        private eventManager: JhiEventManager
    ) {}
    ngOnInit() {
        this.isSaving = false;
        this.courseService.query().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body;
            },
            (res: HttpResponse<Course[]>) => this.onError(res.body)
        );
        this.releaseDate = new Date(this.programmingExercise.releaseDate || undefined);
        this.dueDate = new Date(this.programmingExercise.dueDate || undefined);
        this.releaseClockToggled = false;
        this.dueClockToggled = false;
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    toggleClock(input: string) {
        switch (input) {
            case 'releaseDate':
                this.releaseClockToggled = !this.releaseClockToggled;
                break;
            case 'dueDate':
                this.dueClockToggled = !this.dueClockToggled;
                break;
        }
    }

    save() {
        this.isSaving = true;
        this.programmingExercise.type = 'programming-exercise';
        this.programmingExercise.releaseDate = moment(this.releaseDate).format();
        this.programmingExercise.dueDate = moment(this.dueDate).format();
        if (this.programmingExercise.id !== undefined) {
            this.subscribeToSaveResponse(this.programmingExerciseService.update(this.programmingExercise));
        } else {
            this.subscribeToSaveResponse(this.programmingExerciseService.create(this.programmingExercise));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<ProgrammingExercise>>) {
        result.subscribe(
            (res: HttpResponse<ProgrammingExercise>) => this.onSaveSuccess(res.body),
            (res: HttpErrorResponse) => this.onSaveError()
        );
    }

    private onSaveSuccess(result: ProgrammingExercise) {
        this.eventManager.broadcast({ name: 'programmingExerciseListModification', content: 'OK' });
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError() {
        this.isSaving = false;
    }

    private onError(error: any) {
        this.jhiAlertService.error(error.message, null, null);
    }

    trackCourseById(index: number, item: Course) {
        return item.id;
    }
}

@Component({
    selector: 'jhi-programming-exercise-popup',
    template: ''
})
export class ProgrammingExercisePopupComponent implements OnInit, OnDestroy {
    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private programmingExercisePopupService: ProgrammingExercisePopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe(params => {
            if (params['id']) {
                this.programmingExercisePopupService.open(
                    ProgrammingExerciseDialogComponent as Component,
                    params['id']
                );
            } else {
                if (params['courseId']) {
                    this.programmingExercisePopupService.open(
                        ProgrammingExerciseDialogComponent as Component,
                        undefined,
                        params['courseId']
                    );
                } else {
                    this.programmingExercisePopupService.open(ProgrammingExerciseDialogComponent as Component);
                }
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
