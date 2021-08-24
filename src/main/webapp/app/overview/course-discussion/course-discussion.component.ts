import { Component, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { PageType } from 'app/shared/metis/metis.util';
import { Subscription } from 'rxjs';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-course-discussion',
    templateUrl: './course-discussion.component.html',
    styleUrls: ['./course-discussion.scss'],
})
export class CourseDiscussionComponent implements OnDestroy {
    readonly pageType = PageType.OVERVIEW;
    course?: Course;

    private paramSubscription: Subscription;

    constructor(private activatedRoute: ActivatedRoute, private courseCalculationService: CourseScoreCalculationService) {
        this.paramSubscription = this.activatedRoute.parent!.params.subscribe((params) => {
            const courseId = parseInt(params['courseId'], 10);
            this.course = this.courseCalculationService.getCourse(courseId);
        });
    }

    ngOnDestroy(): void {
        this.paramSubscription?.unsubscribe();
    }
}
