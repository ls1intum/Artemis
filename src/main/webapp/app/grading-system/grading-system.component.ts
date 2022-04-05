import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';

@Component({
    selector: 'jhi-grading-system',
    templateUrl: './grading-system.component.html',
    styleUrls: ['./grading-system.component.scss'],
})
export class GradingSystemComponent implements OnInit {
    courseId?: number;
    examId?: number;
    isExam = false;

    constructor(private route: ActivatedRoute) {}

    ngOnInit(): void {
        this.route.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
            if (params['examId']) {
                this.examId = Number(params['examId']);
                this.isExam = true;
            }
        });
    }
}
