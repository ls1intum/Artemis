import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Observable, Subject, Subscription, merge } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { EventManager } from 'app/core/util/event-manager.service';
import { ExerciseFilter } from 'app/entities/exercise-filter.model';
import { Exercise } from 'app/entities/exercise.model';

interface DeletionServiceInterface {
    delete: (id: number) => Observable<HttpResponse<any>>;
}

@Component({ template: '' })
export abstract class ExerciseComponent implements OnInit, OnDestroy {
    private courseService = inject(CourseManagementService);
    protected translateService = inject(TranslateService);
    private route = inject(ActivatedRoute);
    protected eventManager = inject(EventManager);

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

    selectedExercises: Exercise[] = [];
    allChecked = false;

    // These two variables are used to emit errors to the delete dialog
    protected dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    protected abstract get exercises(): Exercise[];

    public constructor() {
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

    /**
     * Deletes all the given exercises (does not work for programming exercises)
     * @param exercisesToDelete the exercise objects which are to be deleted
     * @param exerciseService service that is used to delete the exercise
     */
    deleteMultipleExercises(exercisesToDelete: Exercise[], exerciseService: DeletionServiceInterface) {
        const deletionObservables = exercisesToDelete.map((exercise) => exerciseService.delete(exercise.id!));
        return merge(...deletionObservables).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: this.getChangeEventName(),
                    content: 'Deleted selected Exercises',
                });
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    toggleExercise(exercise: Exercise) {
        const exerciseIndex = this.selectedExercises.indexOf(exercise);
        if (exerciseIndex !== -1) {
            this.selectedExercises.splice(exerciseIndex, 1);
        } else {
            this.selectedExercises.push(exercise);
        }
        this.allChecked = this.selectedExercises.length === this.exercises.length;
    }

    toggleMultipleExercises(exercises: Exercise[]) {
        this.selectedExercises = [];
        if (!this.allChecked) {
            this.selectedExercises = this.selectedExercises.concat(exercises);
        }
        this.allChecked = this.selectedExercises.length === this.exercises.length;
    }

    isExerciseSelected(exercise: Exercise) {
        return this.selectedExercises.includes(exercise);
    }
}
