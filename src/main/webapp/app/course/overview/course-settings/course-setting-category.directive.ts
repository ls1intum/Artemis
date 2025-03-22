import { Directive, OnDestroy, OnInit, inject } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { ActivatedRoute } from '@angular/router';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { Subscription } from 'rxjs';

/**
 * Base directive for course setting category components.
 * Handles common functionality such as course retrieval and lifecycle management.
 * Child components should implement the abstract methods to respond to course data availability.
 */
@Directive({
    selector: '[jhiCourseSettingCategory]',
})
export abstract class CourseSettingCategoryDirective implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private courseStorageService = inject(CourseStorageService);

    private parentParamSubscription: Subscription;
    private courseUpdatesSubscription: Subscription;

    course?: Course;
    courseId: number;

    ngOnInit(): void {
        if (this.route.parent) {
            this.parentParamSubscription = this.route.parent!.params.subscribe((params) => {
                this.courseId = Number(params.courseId);

                if (this.courseId) {
                    this.onCourseIdAvailable();
                }
            });
        }

        this.course = this.courseStorageService.getCourse(this.courseId);

        this.courseUpdatesSubscription = this.courseStorageService.subscribeToCourseUpdates(this.courseId).subscribe((course: Course) => {
            this.course = course;
            this.onCourseAvailable();
        });
    }

    ngOnDestroy(): void {
        if (this.parentParamSubscription) {
            this.parentParamSubscription.unsubscribe();
        }
        if (this.courseUpdatesSubscription) {
            this.courseUpdatesSubscription.unsubscribe();
        }
    }

    /**
     * Called when course data becomes available or is updated.
     * Child classes should implement this method to handle course data.
     */
    abstract onCourseAvailable(): void;

    /**
     * Called when course ID becomes available from route parameters.
     * Child classes should implement this method to initialize based on course ID.
     */
    abstract onCourseIdAvailable(): void;
}
