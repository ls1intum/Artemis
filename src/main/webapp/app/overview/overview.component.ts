import { Component, OnInit } from '@angular/core';
import { Course, CourseScoreCalculationService, CourseService } from 'app/entities/course';
import { HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-overview',
    templateUrl: './overview.component.html',
    styles: []
})
export class OverviewComponent implements OnInit {
    private courses: Course[];

    constructor(private courseService: CourseService, private  courseScoreCalculationService: CourseScoreCalculationService) {
        this.courseService.findAll().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body;
                this.courseScoreCalculationService.setCourses(this.courses);
            },
            (response: string) => console.log(response)
        );
    }

    ngOnInit() {
    }

}
