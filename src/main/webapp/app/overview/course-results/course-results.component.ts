import { Component, OnInit } from '@angular/core';
import { Course } from 'app/entities/course';

@Component({
    selector: 'jhi-course-results',
    templateUrl: './course-results.component.html',
    styleUrls: ['../course-overview.scss']
})
export class CourseResultsComponent implements OnInit {
    course: Course;

    constructor() {
    }

    ngOnInit() {
    }
}
