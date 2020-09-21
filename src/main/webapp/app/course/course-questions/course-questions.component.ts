import { Component, OnInit, OnDestroy } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute } from '@angular/router';
import { CourseManagementService } from 'app/course/manage/course-management.service';

@Component({
    selector: 'jhi-course-questions',
    templateUrl: './course-questions.component.html',
})
export class CourseQuestionsComponent implements OnInit, OnDestroy {
    course: Course;

    paramSub: Subscription;

    constructor(private route: ActivatedRoute, private courseService: CourseManagementService) {}

    /**
     * On init fetch the course
     */
    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.courseService.findOneForQuestionsDashboard(params['courseId']).subscribe((res) => {
                this.course = res.body!;
                console.log(this.course);
            });
        });
    }

    /**
     * On destroy unsubscribe.
     */
    ngOnDestroy() {
        this.paramSub.unsubscribe();
    }
}
