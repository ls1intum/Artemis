import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { TextExercise } from './text-exercise.model';
import { TextExercisePopupService } from './text-exercise-popup.service';
import { TextExerciseService } from './text-exercise.service';
import { Course, CourseService } from '../course';

import { Subscription } from 'rxjs/Subscription';

@Component({
    selector: 'jhi-text-exercise-dialog',
    templateUrl: './text-exercise-dialog.component.html'
})
export class TextExerciseDialogComponent implements OnInit {
    textExercise: TextExercise;
    isSaving: boolean;
    maxScorePattern = '^[1-9]{1}[0-9]{0,4}$'; // make sure max score is a positive natural integer and not too large

    courses: Course[];

    constructor(
        public activeModal: NgbActiveModal,
        private jhiAlertService: JhiAlertService,
        private textExerciseService: TextExerciseService,
        private courseService: CourseService,
        private eventManager: JhiEventManager,
        private router: Router
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.courseService.query().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res)
        );

        this.router.events.subscribe(event => {
            if (event instanceof NavigationEnd) {
                this.activeModal.close();
            }
        });
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    save() {
        this.isSaving = true;
        if (this.textExercise.id !== undefined) {
            this.subscribeToSaveResponse(this.textExerciseService.update(this.textExercise));
        } else {
            this.subscribeToSaveResponse(this.textExerciseService.create(this.textExercise));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<TextExercise>>) {
        result.subscribe(
            (res: HttpResponse<TextExercise>) => this.onSaveSuccess(res.body),
            (res: HttpErrorResponse) => this.onSaveError(res)
        );
    }

    private onSaveSuccess(result: TextExercise) {
        this.eventManager.broadcast({ name: 'textExerciseListModification', content: 'OK' });
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message, null, null);
        this.isSaving = false;
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }

    trackCourseById(index: number, item: Course) {
        return item.id;
    }
}

@Component({
    selector: 'jhi-text-exercise-popup',
    template: ''
})
export class TextExercisePopupComponent implements OnInit, OnDestroy {
    routeSub: Subscription;

    constructor(private route: ActivatedRoute, private textExercisePopupService: TextExercisePopupService) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe(params => {
            if (params['id']) {
                this.textExercisePopupService.open(TextExerciseDialogComponent as Component, params['id']);
            } else if (params['courseId']) {
                this.textExercisePopupService.open(TextExerciseDialogComponent as Component, undefined, params['courseId']);
            } else {
                this.textExercisePopupService.open(TextExerciseDialogComponent as Component);
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
