import { Component, Input, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-course-learning-path',
    templateUrl: './course-learning-path.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseLearningPathComponent implements OnInit {
    @Input()
    courseId: number;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit(): void {
        this.activatedRoute.parent!.parent!.params.subscribe((params) => {
            this.courseId = +params['courseId'];
        });
    }
}
