import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';
import { ExerciseType } from './course.model';
import { Course } from './course.model';

@Component({
    selector: 'jhi-course-overview',
    templateUrl: './course-exercises-overview.component.html'
})
export class CourseExercisesOverviewComponent implements OnInit {
    course: Course;
    private subscription: Subscription;

    isQuizQuestionCollapsed: boolean;
    isProgrammingQuestionCollapsed: boolean;
    isModellingQuestionCollapsed: boolean;
    isFileQuestionCollapsed: boolean;
    isTextQuestionCollapsed: boolean;
    showPreview: boolean;

    isQuestionCollapsed = new Map<ExerciseType, boolean>();

    constructor(private route: ActivatedRoute) {}

    ngOnInit(): void {
        this.subscription = this.route.params.subscribe(params => {
            console.log(params);
        });
        this.showPreview = false;
    }
}
