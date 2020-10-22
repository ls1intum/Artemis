import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Lecture } from 'app/entities/lecture.model';
import { LectureService } from 'app/lecture/lecture.service';
import { debounceTime, finalize, map } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { AlertService } from 'app/core/alert/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { Subject, Subscription } from 'rxjs';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';

@Component({
    selector: 'jhi-lecture-unit-management',
    templateUrl: './lecture-unit-management.component.html',
    styleUrls: ['./lecture-unit-management.component.scss'],
})
export class LectureUnitManagementComponent implements OnInit, OnDestroy {
    selectedUnitIndex = -1;
    lectureId: number;
    lectureUnits: LectureUnit[] = [];
    isLoading = false;
    updateOrderSubject: Subject<any>;
    updateOrderSubjectSubscription: Subscription;
    readonly LectureUnitType = LectureUnitType;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private lectureService: LectureService,
        private alertService: AlertService,
        private lectureUnitService: LectureUnitService,
    ) {}

    ngOnDestroy(): void {
        this.updateOrder();
        this.updateOrderSubjectSubscription.unsubscribe();
    }
    ngOnInit(): void {
        this.updateOrderSubject = new Subject();
        this.isLoading = true;
        this.activatedRoute.params.subscribe((params) => {
            this.lectureId = +params['lectureId'];
            if (this.lectureId) {
                this.lectureService
                    .find(this.lectureId)
                    .pipe(
                        map((response: HttpResponse<Lecture>) => response.body!),
                        finalize(() => {
                            this.isLoading = false;
                        }),
                    )
                    .subscribe(
                        (lecture) => {
                            if (lecture?.lectureUnits) {
                                this.lectureUnits = lecture?.lectureUnits;
                            }
                        },
                        (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
                    );
            }
        });

        this.updateOrderSubjectSubscription = this.updateOrderSubject.pipe(debounceTime(1500)).subscribe(() => {
            this.updateOrder();
        });
    }

    updateOrder() {
        this.lectureUnitService
            .updateOrder(this.lectureId, this.lectureUnits)
            .pipe(map((response: HttpResponse<LectureUnit[]>) => response.body!))
            .subscribe(
                () => {},
                (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            );
    }

    moveUp(index: number): void {
        if (this.lectureUnits) {
            [this.lectureUnits[index], this.lectureUnits[index - 1]] = [this.lectureUnits[index - 1], this.lectureUnits[index]];
            this.selectedUnitIndex = index + 1;
        }
        this.updateOrderSubject.next();
    }

    moveDown(index: number): void {
        if (this.lectureUnits) {
            [this.lectureUnits[index], this.lectureUnits[index + 1]] = [this.lectureUnits[index + 1], this.lectureUnits[index]];
            this.selectedUnitIndex = index - 1;
        }
        this.updateOrderSubject.next();
    }

    createExerciseUnit() {
        this.router.navigate(['exercise-units', 'create'], { relativeTo: this.activatedRoute });
    }

    selectUnselectUnit(index: number) {
        if (this.selectedUnitIndex === index) {
            this.selectedUnitIndex = -1;
        } else {
            this.selectedUnitIndex = index;
        }
    }

    identify(_: any, lectureUnit: LectureUnit) {
        return lectureUnit.id;
    }
}
