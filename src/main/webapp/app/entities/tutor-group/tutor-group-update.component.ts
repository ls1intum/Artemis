import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { JhiAlertService } from 'ng-jhipster';
import { TutorGroupService } from './tutor-group.service';
import { User } from 'app/core';
import { UserService } from 'app/core/user/user.service';
import { Course } from 'app/entities/course';
import { CourseService } from 'app/entities/course/course.service';
import { TutorGroup } from 'app/entities/tutor-group';

@Component({
    selector: 'jhi-tutor-group-update',
    templateUrl: './tutor-group-update.component.html',
})
export class TutorGroupUpdateComponent implements OnInit {
    tutorGroup: TutorGroup;
    isSaving: boolean;

    users: User[];

    courses: Course[];

    constructor(
        protected jhiAlertService: JhiAlertService,
        protected tutorGroupService: TutorGroupService,
        protected userService: UserService,
        protected courseService: CourseService,
        protected activatedRoute: ActivatedRoute,
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ tutorGroup }) => {
            this.tutorGroup = tutorGroup;
        });
        this.userService
            .query()
            .pipe(
                filter((mayBeOk: HttpResponse<User[]>) => mayBeOk.ok),
                map((response: HttpResponse<User[]>) => response.body),
            )
            .subscribe(
                (res: User[]) => (this.users = res),
                (res: HttpErrorResponse) => this.onError(res.message),
            );
        this.courseService
            .query()
            .pipe(
                filter((mayBeOk: HttpResponse<Course[]>) => mayBeOk.ok),
                map((response: HttpResponse<Course[]>) => response.body),
            )
            .subscribe(
                (res: Course[]) => (this.courses = res),
                (res: HttpErrorResponse) => this.onError(res.message),
            );
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.tutorGroup.id !== undefined) {
            this.subscribeToSaveResponse(this.tutorGroupService.update(this.tutorGroup));
        } else {
            this.subscribeToSaveResponse(this.tutorGroupService.create(this.tutorGroup));
        }
    }

    protected subscribeToSaveResponse(result: Observable<HttpResponse<TutorGroup>>) {
        result.subscribe(
            (res: HttpResponse<TutorGroup>) => this.onSaveSuccess(),
            (res: HttpErrorResponse) => this.onSaveError(),
        );
    }

    protected onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    protected onSaveError() {
        this.isSaving = false;
    }

    protected onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, undefined);
    }

    trackUserById(index: number, item: User) {
        return item.id;
    }

    trackCourseById(index: number, item: Course) {
        return item.id;
    }

    getSelected(selectedVals: Array<any>, option: any) {
        if (selectedVals) {
            for (let i = 0; i < selectedVals.length; i++) {
                if (option.id === selectedVals[i].id) {
                    return selectedVals[i];
                }
            }
        }
        return option;
    }
}
