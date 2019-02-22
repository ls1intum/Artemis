import { Component, Input } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { TextExercise } from './text-exercise.model';
import { TextExerciseService } from './text-exercise.service';
import { CourseExerciseService, CourseService } from '../course';
import { ActivatedRoute } from '@angular/router';
import { ExerciseComponent } from 'app/entities/exercise/exercise.component';

@Component({
    selector: 'jhi-text-exercise',
    templateUrl: './text-exercise.component.html'
})
export class TextExerciseComponent extends ExerciseComponent {
    @Input() textExercises: TextExercise[];
    predicate: string;
    reverse: boolean;

    constructor(
        private textExerciseService: TextExerciseService,
        private courseExerciseService: CourseExerciseService,
        courseService: CourseService,
        private jhiAlertService: JhiAlertService,
        eventManager: JhiEventManager,
        route: ActivatedRoute
    ) {
        super(courseService, route, eventManager);
        this.textExercises = [];
        this.predicate = 'id';
        this.reverse = true;
    }

    protected loadExercises(): void {
        this.courseExerciseService.findAllTextExercisesForCourse(this.courseId).subscribe(
            (res: HttpResponse<TextExercise[]>) => {
                this.textExercises = res.body;
                // reconnect exercise with course
                this.textExercises.forEach(textExercise => {
                    textExercise.course = this.course;
                });
                this.emitExerciseCount(this.textExercises.length);
            },
            (res: HttpErrorResponse) => this.onError(res)
        );
    }

    trackId(index: number, item: TextExercise) {
        return item.id;
    }

    protected getChangeEventName(): string {
        return 'textExerciseListModification';
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }

    callback() {}
}
