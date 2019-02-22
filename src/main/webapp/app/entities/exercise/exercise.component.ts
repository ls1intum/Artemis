import { Input, Output, OnInit, OnDestroy, EventEmitter } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';
import { Course, CourseService } from 'app/entities/course';

export abstract class ExerciseComponent implements OnInit, OnDestroy {
    private eventSubscriber: Subscription;
    @Input() embedded = false;
    showAlertHeading: boolean;
    showHeading: boolean;
    courseId: number;
    @Input() course: Course;
    @Output() exerciseCount = new EventEmitter<number>();

    protected constructor(private courseService: CourseService, private route: ActivatedRoute, private eventManager: JhiEventManager) {}

    ngOnInit(): void {
        this.showAlertHeading = !this.embedded;
        this.showHeading = this.embedded;
            this.load();
        this.registerChangeInExercises();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
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
            this.course = courseResponse.body;
            this.loadExercises();
        });
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
