import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { Course } from './course.model';

@Component({
    selector: 'jhi-course-overview',
    templateUrl: './course-overview.component.html'
})
export class CourseOverviewComponent implements OnInit, OnDestroy {
    course: Course;
}
