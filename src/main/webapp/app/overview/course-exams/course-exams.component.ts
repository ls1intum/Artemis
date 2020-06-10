import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Exam } from 'app/entities/exam.model';

@Component({
    selector: 'jhi-course-exams',
    templateUrl: './course-exams.component.html',
    styleUrls: ['./course-exams.scss'],
})
export class CourseExamsComponent implements OnInit {
    courseId: number;
    exams: Exam[];

    constructor(private route: ActivatedRoute) {}

    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
    }
}
