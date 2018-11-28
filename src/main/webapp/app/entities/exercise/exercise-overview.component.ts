import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';
import { ExerciseType } from '../course/course.model';
import { Course } from '../course/course.model';

@Component({
    selector: 'jhi-exercise-overview-component',
    templateUrl: './exercise-overview.component.html',
    styles: []
})
export class ExerciseOverviewComponent implements OnInit {
    course: Course;
    private subscription: Subscription;

    isCollapsed: boolean;
    showPreview: boolean;
    items = ['quiz', 'programming', 'text', 'modeling', 'upload'];

    constructor(private route: ActivatedRoute) {}

    ngOnInit(): void {
        this.subscription = this.route.params.subscribe(params => {
            console.log(params);
        });
        this.showPreview = false;
    }
}
