import { EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';
import { Course, CourseService } from 'app/entities/course';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';

export abstract class ExerciseComponent implements OnInit, OnDestroy {
    private eventSubscriber: Subscription;
    @Input() embedded = false;
    @Input() course: Course;
    @Output() exerciseCount = new EventEmitter<number>();
    showAlertHeading: boolean;
    showHeading: boolean;
    courseId: number;
    predicate: string;
    reverse: boolean;

    // These two variables are used to emit errors to the delete dialog
    protected dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    protected constructor(
        private courseService: CourseService,
        protected translateService: TranslateService,
        private route: ActivatedRoute,
        protected eventManager: JhiEventManager,
    ) {
        this.predicate = 'id';
        this.reverse = true;
    }

    ngOnInit(): void {
        this.showAlertHeading = !this.embedded;
        this.showHeading = this.embedded;
        this.load();
        this.registerChangeInExercises();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
        this.dialogErrorSource.unsubscribe();
    }

    protected load(): void {
        if (this.course == null) {
            this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
            this.loadCourse();
        } else {
            this.courseId = this.course.id;
            this.loadExercises();
        }
    }

    private loadCourse(): void {
        this.courseService.find(this.courseId).subscribe(courseResponse => {
            this.course = courseResponse.body!;
            this.loadExercises();
        });
    }

    public getAmountOfExercisesString<T>(exercises: Array<T>): string {
        if (exercises.length === 0) {
            return this.translateService.instant('artemisApp.createExercise.noExercises');
        } else {
            return exercises.length.toString();
        }
    }

    protected abstract loadExercises(): void;

    protected emitExerciseCount(count: number): void {
        this.exerciseCount.emit(count);
    }

    protected abstract getChangeEventName(): string;

    private registerChangeInExercises() {
        this.eventSubscriber = this.eventManager.subscribe(this.getChangeEventName(), () => this.load());
    }
}
