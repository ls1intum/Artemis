import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { Course } from './course.model';
import { CoursePopupService } from './course-popup.service';
import { CourseService } from './course.service';

import * as moment from 'moment';

@Component({
    selector: 'jhi-course-dialog',
    templateUrl: './course-dialog.component.html'
})
export class CourseDialogComponent implements OnInit {

    course: Course;
    isSaving: boolean;
    startDate: Date;
    endDate: Date;
    endClockToggled = false;
    startClockToggled = false;

    constructor(
        public activeModal: NgbActiveModal,
        private courseService: CourseService,
        private eventManager: JhiEventManager
    ) {
    }

    ngOnInit() {
        this.isSaving = false;
        this.startDate = new Date(this.course.startDate || undefined);
        this.endDate = new Date(this.course.endDate || undefined);
        console.log(this.course);
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    toggleClock(input: string) {
      switch ( input ) {
        case 'startDate':
          this.startClockToggled = !this.startClockToggled;
          break;
        case 'endDate':
          this.endClockToggled = !this.endClockToggled;
          break;
      }
    }

    save() {
        this.isSaving = true;

        this.course.startDate = moment(this.startDate).format();
        this.course.endDate = moment(this.endDate).format();

        if (this.course.id !== undefined) {
            this.subscribeToSaveResponse(
                this.courseService.update(this.course));
        } else {
            this.subscribeToSaveResponse(
                this.courseService.create(this.course));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<Course>>) {
        result.subscribe((res: HttpResponse<Course>) =>
            this.onSaveSuccess(res.body), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess(result: Course) {
        this.eventManager.broadcast({ name: 'courseListModification', content: 'OK'});
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError() {
        this.isSaving = false;
    }
}

@Component({
    selector: 'jhi-course-popup',
    template: ''
})
export class CoursePopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private coursePopupService: CoursePopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe(params => {
            if ( params['id'] ) {
                this.coursePopupService
                    .open(CourseDialogComponent as Component, params['id']);
            } else {
                this.coursePopupService
                    .open(CourseDialogComponent as Component);
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
