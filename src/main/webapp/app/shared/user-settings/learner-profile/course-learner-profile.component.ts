import { Component, OnInit } from '@angular/core';
import { EditableSliderComponent } from 'app/shared/editable-slider/editable-slider.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-course-learner-profile',
    templateUrl: './course-learner-profile.component.html',
    standalone: true,
    imports: [EditableSliderComponent],
})
export class CourseLearnerProfileComponent implements OnInit {
    courses: Course[];

    constructor(private courseService: CourseManagementService) {}

    async ngOnInit() {
        this.loadCourses();
        console.log(this.courses);
    }

    loadCourses() {
        this.courseService.findAllForDropdown().subscribe({
            next: (res: HttpResponse<Course[]>) => {
                if (!res.body || res.body.length === 0) {
                    return;
                }
                this.courses = res.body;
            },
        });
    }
}
