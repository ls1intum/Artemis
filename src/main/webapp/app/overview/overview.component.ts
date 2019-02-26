import { Component } from '@angular/core';
import { Course, CourseScoreCalculationService, CourseService } from 'app/entities/course';
import { HttpResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';


@Component({
    selector: 'jhi-overview',
    templateUrl: './overview.component.html',
    styles: []
})
export class OverviewComponent {
    public courses: Course[];

    constructor(private courseService: CourseService, private jhiAlertService: JhiAlertService, private  courseScoreCalculationService: CourseScoreCalculationService) {
        this.courseService.findAll().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body;
                this.courseScoreCalculationService.setCourses(this.courses);
            },
            (response: string) => this.onError(response)
        );
    }

    private onError(error: string) {
        this.jhiAlertService.error(error, null, null);
    }

}
