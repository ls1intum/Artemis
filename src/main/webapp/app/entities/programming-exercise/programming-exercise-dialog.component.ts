import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';

import { Observable } from 'rxjs';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { ProgrammingExercise } from './programming-exercise.model';
import { ProgrammingExercisePopupService } from './programming-exercise-popup.service';
import { ProgrammingExerciseService } from './services/programming-exercise.service';
import { Course } from '../course';
import { CourseService } from 'app/entities/course/course.service';

import { Subscription } from 'rxjs/Subscription';
import { ExerciseCategory, ExerciseService } from 'app/entities/exercise';
import { FileService } from 'app/shared/http/file.service';
import { MAX_SCORE_PATTERN } from 'app/app.constants';

@Component({
    selector: 'jhi-programming-exercise-dialog',
    templateUrl: './programming-exercise-dialog.component.html',
    styleUrls: ['./programming-exercise-form.scss'],
})
export class ProgrammingExerciseDialogComponent implements OnInit {
    programmingExercise: ProgrammingExercise;
    isSaving: boolean;
    maxScorePattern = MAX_SCORE_PATTERN;

    courses: Course[];
    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];
    problemStatementLoaded = false;
    templateParticipationResultLoaded = true;
    notificationText: string | null;

    constructor(
        public activeModal: NgbActiveModal,
        private jhiAlertService: JhiAlertService,
        private programmingExerciseService: ProgrammingExerciseService,
        private courseService: CourseService,
        private fileService: FileService,
        private exerciseService: ExerciseService,
        private eventManager: JhiEventManager,
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.notificationText = null;
        this.courseService.query().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body!;
            },
            (res: HttpErrorResponse) => this.onError(res),
        );
        this.exerciseCategories = this.exerciseService.convertExerciseCategoriesFromServer(this.programmingExercise);
        this.courseService.findAllCategoriesOfCourse(this.programmingExercise.course!.id).subscribe(
            (res: HttpResponse<string[]>) => {
                this.existingCategories = this.exerciseService.convertExerciseCategoriesAsStringFromServer(res.body!);
            },
            (res: HttpErrorResponse) => this.onError(res),
        );
        // If an exercise is created, load our readme template so the problemStatement is not empty
        if (this.programmingExercise.id === undefined) {
            this.fileService.getTemplateFile('readme', this.programmingExercise.programmingLanguage).subscribe(
                file => {
                    this.programmingExercise.problemStatement = file;
                    this.problemStatementLoaded = true;
                },
                err => {
                    this.programmingExercise.problemStatement = '';
                    this.problemStatementLoaded = true;
                    console.log('Error while getting template instruction file!', err);
                },
            );
        } else {
            this.problemStatementLoaded = true;
            this.templateParticipationResultLoaded = false;
        }
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    updateCategories(categories: ExerciseCategory[]) {
        this.programmingExercise.categories = categories.map(el => JSON.stringify(el));
    }

    save() {
        this.isSaving = true;
        if (this.programmingExercise.id !== undefined) {
            const requestOptions = {} as any;
            if (this.notificationText) {
                requestOptions.notificationText = this.notificationText;
            }
            this.subscribeToSaveResponse(this.programmingExerciseService.update(this.programmingExercise, requestOptions));
        } else {
            this.subscribeToSaveResponse(this.programmingExerciseService.create(this.programmingExercise));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<ProgrammingExercise>>) {
        result.subscribe(
            (res: HttpResponse<ProgrammingExercise>) => this.onSaveSuccess(res.body!),
            (res: HttpErrorResponse) => this.onSaveError(res),
        );
    }

    private onSaveSuccess(result: ProgrammingExercise) {
        this.eventManager.broadcast({ name: 'programmingExerciseListModification', content: 'OK' });
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.headers.get('X-artemisApp-error')!);
        this.isSaving = false;
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.headers.get('X-artemisApp-error')!);
    }

    trackCourseById(index: number, item: Course) {
        return item.id;
    }
}

@Component({
    selector: 'jhi-programming-exercise-popup',
    template: '',
})
export class ProgrammingExercisePopupComponent implements OnInit, OnDestroy {
    routeSub: Subscription;

    constructor(private route: ActivatedRoute, private programmingExercisePopupService: ProgrammingExercisePopupService) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe(params => {
            if (params['id']) {
                this.programmingExercisePopupService.open(ProgrammingExerciseDialogComponent as Component, params['id']);
            } else {
                if (params['courseId']) {
                    this.programmingExercisePopupService.open(ProgrammingExerciseDialogComponent as Component, undefined, params['courseId']);
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
