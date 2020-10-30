import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { Lecture } from 'app/entities/lecture.model';
import { LectureService } from 'app/lecture/lecture.service';
import { debounceTime, filter, finalize, map } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { JhiAlertService } from 'ng-jhipster';
import { onError } from 'app/shared/util/global.utils';
import { Subject, Subscription } from 'rxjs';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';

@Component({
    selector: 'jhi-lecture-unit-management',
    templateUrl: './lecture-unit-management.component.html',
    styleUrls: ['./lecture-unit-management.component.scss'],
})
export class LectureUnitManagementComponent implements OnInit, OnDestroy {
    lectureId: number;
    lectureUnits: LectureUnit[] = [];
    isLoading = false;
    updateOrderSubject: Subject<any>;
    updateOrderSubjectSubscription: Subscription;
    navigationEndSubscription: Subscription;
    readonly LectureUnitType = LectureUnitType;
    readonly ActionType = ActionType;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private lectureService: LectureService,
        private alertService: JhiAlertService,
        private lectureUnitService: LectureUnitService,
    ) {}

    ngOnDestroy(): void {
        this.updateOrder();
        this.updateOrderSubjectSubscription.unsubscribe();
        this.dialogErrorSource.unsubscribe();
        this.navigationEndSubscription.unsubscribe();
    }

    ngOnInit(): void {
        this.navigationEndSubscription = this.router.events.pipe(filter((value) => value instanceof NavigationEnd)).subscribe(() => {
            this.loadData();
        });

        this.updateOrderSubject = new Subject();
        this.activatedRoute.params.subscribe((params) => {
            this.lectureId = +params['lectureId'];
            if (this.lectureId) {
                this.loadData();
            }
        });

        // debounceTime limits the amount of put requests sent for updating the lecture unit order
        this.updateOrderSubjectSubscription = this.updateOrderSubject.pipe(debounceTime(1000)).subscribe(() => {
            this.updateOrder();
        });
    }

    loadData() {
        this.isLoading = true;
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
                    } else {
                        this.lectureUnits = [];
                    }
                },
                (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            );
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
        }
        this.updateOrderSubject.next('up');
    }

    moveDown(index: number): void {
        if (this.lectureUnits) {
            [this.lectureUnits[index], this.lectureUnits[index + 1]] = [this.lectureUnits[index + 1], this.lectureUnits[index]];
        }
        this.updateOrderSubject.next('down');
    }

    createExerciseUnit() {
        this.router.navigate(['exercise-units', 'create'], { relativeTo: this.activatedRoute });
    }

    createAttachmentUnit() {
        this.router.navigate(['attachment-units', 'create'], { relativeTo: this.activatedRoute });
    }

    identify(index: number, lectureUnit: LectureUnit) {
        return `${index}-${lectureUnit.id}`;
    }

    getDeleteQuestionKey(lectureUnit: LectureUnit) {
        if (lectureUnit.type === LectureUnitType.EXERCISE) {
            return 'artemisApp.exerciseUnit.delete.question';
        }
        if (lectureUnit.type === LectureUnitType.ATTACHMENT) {
            return 'artemisApp.attachmentUnit.delete.question';
        }
    }

    getDeleteConfirmationTextKey(lectureUnit: LectureUnit) {
        if (lectureUnit.type === LectureUnitType.EXERCISE) {
            return 'artemisApp.exerciseUnit.delete.typeNameToConfirm';
        }
        if (lectureUnit.type === LectureUnitType.ATTACHMENT) {
            return 'artemisApp.attachmentUnit.delete.typeNameToConfirm';
        }
    }

    getActionType(lectureUnit: LectureUnit) {
        if (lectureUnit.type === LectureUnitType.EXERCISE) {
            return ActionType.Unlink;
        } else {
            return ActionType.Delete;
        }
    }

    deleteLectureUnit(lectureUnitId: number) {
        this.lectureUnitService.delete(lectureUnitId).subscribe(
            () => {
                this.dialogErrorSource.next('');
                this.loadData();
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }

    editButtonAvailable(lectureUnit: LectureUnit) {
        switch (lectureUnit?.type) {
            case LectureUnitType.ATTACHMENT:
            case LectureUnitType.TEXT:
            case LectureUnitType.VIDEO:
                return true;
            default:
                return false;
        }
    }

    editButtonClicked(lectureUnit: LectureUnit) {
        switch (lectureUnit?.type) {
            case LectureUnitType.ATTACHMENT:
                this.router.navigate(['attachment-units', lectureUnit.id, 'edit'], { relativeTo: this.activatedRoute });
                break;
            default:
                return;
        }
    }

    getLectureUnitName(lectureUnit: LectureUnit): string | undefined {
        switch (lectureUnit?.type) {
            case LectureUnitType.ATTACHMENT:
                return (<AttachmentUnit>lectureUnit)?.attachment?.name;
            case LectureUnitType.EXERCISE:
                return (<ExerciseUnit>lectureUnit)?.exercise?.title;
            default:
                return lectureUnit.name;
        }
    }
}
