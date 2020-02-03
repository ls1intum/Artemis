import { Component, OnInit } from '@angular/core';
import { Course } from 'app/entities/course';
import { CourseService } from 'app/entities/course/course.service';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { HttpResponse } from '@angular/common/http';
import { CachingStrategy } from 'app/shared';
import { CourseScoreCalculationService } from 'app/overview';
import { isIntelliJ } from 'app/intellij/intellij';

const DESCRIPTION_READ = 'isDescriptionRead';

@Component({
    selector: 'jhi-course-overview',
    templateUrl: './course-overview.component.html',
    styleUrls: ['course-overview.scss'],
})
export class CourseOverviewComponent implements OnInit {
    readonly isIntelliJ = isIntelliJ;
    CachingStrategy = CachingStrategy;
    private courseId: number;
    private subscription: Subscription;
    public course: Course | null;
    public courseDescription: string;
    public enableShowMore: boolean;
    public longTextShown: boolean;

    constructor(
        private courseService: CourseService,
        private courseCalculationService: CourseScoreCalculationService,
        private courseServer: CourseService,
        private route: ActivatedRoute,
    ) {}

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.course = this.courseCalculationService.getCourse(this.courseId);
        if (!this.course) {
            this.courseService.findAll().subscribe((res: HttpResponse<Course[]>) => {
                this.courseCalculationService.setCourses(res.body!);
                this.course = this.courseCalculationService.getCourse(this.courseId);
                this.adjustCourseDescription();
            });
        }
        this.adjustCourseDescription();
    }

    adjustCourseDescription() {
        if (this.course && this.course.description) {
            this.enableShowMore = this.course.description.length > 50;
            if (localStorage.getItem(DESCRIPTION_READ + this.course.shortName) && !this.courseDescription && this.enableShowMore) {
                this.showShortDescription();
            } else {
                this.showLongDescription();
                localStorage.setItem(DESCRIPTION_READ + this.course.shortName, 'true');
            }
        }
    }

    showLongDescription() {
        this.courseDescription = this.course!.description;
        this.longTextShown = true;
    }

    showShortDescription() {
        this.courseDescription = this.course!.description.substr(0, 50) + '...';
        this.longTextShown = false;
    }
}
