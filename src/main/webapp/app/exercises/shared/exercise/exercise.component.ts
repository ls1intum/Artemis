import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { EventManager } from 'app/core/util/event-manager.service';
import { ExerciseFilter } from 'app/entities/exercise-filter.model';

@Component({ template: '' })
export abstract class ExerciseComponent implements OnInit, OnDestroy {
    private eventSubscriber: Subscription;
    @Input() embedded = false;
    @Input() course: Course;
    filter: ExerciseFilter;
    @Output() exerciseCount = new EventEmitter<number>();
    @Output() filteredExerciseCount = new EventEmitter<number>();
    showHeading: boolean;
    courseId: number;
    predicate: string;
    reverse: boolean;

    // These two variables are used to emit errors to the delete dialog
    protected dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    protected constructor(
        private courseService: CourseManagementService,
        protected translateService: TranslateService,
        private route: ActivatedRoute,
        protected eventManager: EventManager,
    ) {
        this.predicate = 'id';
        this.reverse = true;
    }

    /**
     * Fetches an exercise from the server (and if needed the course as well)
     */
    ngOnInit(): void {
        this.showHeading = this.embedded;
        this.load();
        this.registerChangeInExercises();
        this.filter = new ExerciseFilter();
    }

    /**
     * Unsubscribes from all subscriptions
     */
    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
        this.dialogErrorSource.unsubscribe();
    }

    @Input()
    set exerciseFilter(value: ExerciseFilter) {
        this.filter = value;
        this.applyFilter();
    }

    protected load(): void {
        if (!this.course?.id) {
            this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
            this.loadCourse();
        } else {
            this.courseId = this.course.id;
            this.loadExercises();
        }
    }

    private loadCourse(): void {
        this.courseService.find(this.courseId).subscribe((courseResponse) => {
            this.course = courseResponse.body!;
            this.loadExercises();
        });
    }

    /**
     * Returns the number of exercises given as a string
     * @param exercises Exercises which to count
     */
    public getAmountOfExercisesString<T>(exercises: Array<T>): string {
        if (exercises.length === 0) {
            return this.translateService.instant('artemisApp.createExercise.noExercises');
        } else {
            return exercises.length.toString();
        }
    }

    protected abstract loadExercises(): void;

    protected abstract applyFilter(): void;

    protected emitExerciseCount(count: number): void {
        this.exerciseCount.emit(count);
    }

    protected emitFilteredExerciseCount(count: number): void {
        this.filteredExerciseCount.emit(count);
    }

    protected abstract getChangeEventName(): string;

    private registerChangeInExercises() {
        this.eventSubscriber = this.eventManager.subscribe(this.getChangeEventName(), () => this.load());
    }
}
