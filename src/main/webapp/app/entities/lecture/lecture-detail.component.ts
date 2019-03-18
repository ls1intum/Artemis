import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Lecture } from 'app/entities/lecture';

@Component({
    selector: 'jhi-lecture-detail',
    templateUrl: './lecture-detail.component.html'
})
export class LectureDetailComponent implements OnInit {
    lecture: Lecture;

    constructor(protected activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ lecture }) => {
            this.lecture = lecture;
        });
    }

    previousState() {
        window.history.back();
    }
}
