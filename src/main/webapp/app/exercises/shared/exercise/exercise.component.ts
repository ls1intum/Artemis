import { EventEmitter, Input, OnDestroy, OnInit, Output, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { Course } from 'app/entities/course.model';

@Component({ template: '' })
export abstract class ExerciseComponent implements OnInit, OnDestroy {
    private eventSubscriber: Subscription;
    @Input() embedded = false;
    @Input() course: Course;
    @Input() isInExerciseGroup = false;
    @Output() exerciseCount = new EventEmitter<number>();
    @Output() onDeleteExercise = new EventEmitter<{ exerciseId: number; groupId: number }>();
    showAlertHeading: boolean;
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
        protected eventManager: JhiEventManager,
    ) {
        this.predicate = 'id';
        this.reverse = true;
    }

    /**
     * Fetches an exercise from the server (and if needed the course as well)
     */
    ngOnInit(): void {
        this.showAlertHeading = !this.embedded;
        this.showHeading = this.embedded;
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        if (!this.isInExerciseGroup) {
            this.load();
        }
        this.registerChangeInExercises();
    }

    /**
     * Unsubscribes from all subscriptions
     */
    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
        this.dialogErrorSource.unsubscribe();
    }

    protected load(): void {
        if (!this.course?.id) {
            this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
            this.loadCourse();
        } else {
            this.courseId = this.course.id;
            if (!this.isInExerciseGroup) {
                this.loadExercises();
            }
        }
    }

    private loadCourse(): void {
        this.courseService.find(this.courseId).subscribe((courseResponse) => {
            this.course = courseResponse.body!;
            if (!this.isInExerciseGroup) {
                this.loadExercises();
            }
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

    protected emitExerciseCount(count: number): void {
        this.exerciseCount.emit(count);
    }

    protected abstract getChangeEventName(): string;

    emitDeleteEvent(exerciseId: number, groupId: number) {
        this.onDeleteExercise.emit({ exerciseId, groupId });
    }

    private registerChangeInExercises() {
        this.eventSubscriber = this.eventManager.subscribe(this.getChangeEventName(), () => {
            if (this.isInExerciseGroup) {
                location.reload();
            } else {
                this.load();
            }
        });
    }
}
