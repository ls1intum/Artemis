import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Lecture } from 'app/entities/lecture.model';

@Component({
    selector: 'jhi-lecture-detail',
    templateUrl: './lecture-detail.component.html',
})
export class LectureDetailComponent implements OnInit {
    lecture: Lecture;

    constructor(private activatedRoute: ActivatedRoute) {}

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit() {
        this.activatedRoute.data.subscribe(({ lecture }) => {
            this.lecture = lecture;
        });
    }
}
