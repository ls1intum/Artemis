import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { Lecture } from 'app/entities/lecture.model';
import { filter } from 'rxjs/operators';

@Component({
    selector: 'jhi-lecture-detail',
    templateUrl: './lecture-detail.component.html',
})
export class LectureDetailComponent implements OnInit {
    lecture: Lecture;
    isVisible: boolean;

    constructor(private activatedRoute: ActivatedRoute, private router: Router) {}

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit() {
        this.activatedRoute.data.subscribe(({ lecture }) => {
            this.lecture = lecture;
        });
        this.isVisible = this.activatedRoute.children.length === 0;
        this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => (this.isVisible = this.activatedRoute.children.length === 0));
    }
}
