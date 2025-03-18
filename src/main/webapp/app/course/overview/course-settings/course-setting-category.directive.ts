import { Directive, OnDestroy, OnInit, inject } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { ActivatedRoute } from '@angular/router';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { Subscription } from 'rxjs';

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
                this.onCourseIdAvailable();
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

    abstract onCourseAvailable(): void;
    abstract onCourseIdAvailable(): void;
}
