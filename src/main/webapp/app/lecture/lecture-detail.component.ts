import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { faFile, faPencilAlt, faPuzzlePiece } from '@fortawesome/free-solid-svg-icons';
import { Lecture } from 'app/entities/lecture.model';

@Component({
    selector: 'jhi-lecture-detail',
    templateUrl: './lecture-detail.component.html',
})
export class LectureDetailComponent implements OnInit {
    lecture: Lecture;

    // Icons
    faPencilAlt = faPencilAlt;
    faFile = faFile;
    faPuzzlePiece = faPuzzlePiece;

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
