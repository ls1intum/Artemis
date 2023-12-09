import { Component, OnInit } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from '../course/manage/course-management.service';
import { HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-lti-courses-overview',
    templateUrl: './lti13-select-course.component.html',
})
export class LtiCoursesComponent implements OnInit {
    public courses: Course[];
    constructor(private courseService: CourseManagementService) {}

    async ngOnInit() {
        this.loadAndFilterCourses();
    }

    loadAndFilterCourses() {
        this.courseService.findAllForDashboard().subscribe({
            next: (res: HttpResponse<Course[]>) => {
                if (res.body) {
                    this.courses = res.body!.sort((a, b) => (a.title ?? '').localeCompare(b.title ?? ''));
                    this.courses = this.courses.filter((course) => course.onlineCourse);
                }
            },
        });
    }
}
