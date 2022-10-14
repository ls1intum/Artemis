import { Component, OnDestroy, OnInit, Input } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { Lecture } from 'app/entities/lecture.model';
import { LectureService } from 'app/lecture/lecture.service';
import { debounceTime, filter, finalize, map } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { Subject, Subscription } from 'rxjs';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { faPencilAlt, faTimes } from '@fortawesome/free-solid-svg-icons';
import { CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';

@Component({
    selector: 'jhi-lecture-unit-management',
    templateUrl: './lecture-unit-management.component.html',
    styleUrls: ['./lecture-unit-management.component.scss'],
})
export class LectureUnitManagementComponent implements OnInit, OnDestroy {
    @Input() showCreationCard = true;
    @Input() showLearningGoals = true;

    lectureId: number;
    lectureUnits: LectureUnit[] = [];
    lecture: Lecture;
    isLoading = false;
    updateOrderSubject: Subject<any>;
    updateOrderSubjectSubscription: Subscription;
    navigationEndSubscription: Subscription;
    readonly LectureUnitType = LectureUnitType;
    readonly ActionType = ActionType;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faTimes = faTimes;
    faPencilAlt = faPencilAlt;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private lectureService: LectureService,
        private alertService: AlertService,
        public lectureUnitService: LectureUnitService,
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
        this.activatedRoute.parent!.params.subscribe((params) => {
            this.lectureId = +params['lectureId'];
            if (this.lectureId) {
                // TODO: the lecture (without units) is already available through the lecture.route.ts resolver, it's not really good that we load it twice
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
        // TODO: we actually would like to have the lecture with all units! Posts and learning goals are not required here
        // we could also simply load all units for the lecture (as the lecture is already available through the route, see TODO above)
        this.lectureService
            .findWithDetails(this.lectureId)
            .pipe(
                map((response: HttpResponse<Lecture>) => response.body!),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (lecture) => {
                    this.lecture = lecture;
                    if (lecture?.lectureUnits) {
                        this.lectureUnits = lecture?.lectureUnits;
                    } else {
                        this.lectureUnits = [];
                    }
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }

    updateOrder() {
        this.lectureUnitService
            .updateOrder(this.lectureId, this.lectureUnits)
            .pipe(map((response: HttpResponse<LectureUnit[]>) => response.body!))
            .subscribe({
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }

    drop(event: CdkDragDrop<LectureUnit[]>) {
        moveItemInArray(this.lectureUnits, event.previousIndex, event.currentIndex);
        this.updateOrderSubject.next('');
    }

    identify(index: number, lectureUnit: LectureUnit) {
        return `${index}-${lectureUnit.id}`;
    }

    getDeleteQuestionKey(lectureUnit: LectureUnit) {
        switch (lectureUnit.type) {
            case LectureUnitType.EXERCISE:
                return 'artemisApp.exerciseUnit.delete.question';
            case LectureUnitType.ATTACHMENT:
                return 'artemisApp.attachmentUnit.delete.question';
            case LectureUnitType.VIDEO:
                return 'artemisApp.videoUnit.delete.question';
            case LectureUnitType.TEXT:
                return 'artemisApp.textUnit.delete.question';
            default:
                return '';
        }
    }

    getDeleteConfirmationTextKey(lectureUnit: LectureUnit) {
        switch (lectureUnit.type) {
            case LectureUnitType.EXERCISE:
                return 'artemisApp.exerciseUnit.delete.typeNameToConfirm';
            case LectureUnitType.ATTACHMENT:
                return 'artemisApp.attachmentUnit.delete.typeNameToConfirm';
            case LectureUnitType.VIDEO:
                return 'artemisApp.videoUnit.delete.typeNameToConfirm';
            case LectureUnitType.TEXT:
                return 'artemisApp.textUnit.delete.typeNameToConfirm';
            default:
                return '';
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
        this.lectureUnitService.delete(lectureUnitId, this.lectureId).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.loadData();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    editButtonAvailable(lectureUnit: LectureUnit) {
        switch (lectureUnit?.type) {
            case LectureUnitType.ATTACHMENT:
            case LectureUnitType.TEXT:
            case LectureUnitType.VIDEO:
            case LectureUnitType.ONLINE:
                return true;
            default:
                return false;
        }
    }

    editButtonRouterLink(lectureUnit: LectureUnit) {
        switch (lectureUnit?.type) {
            case LectureUnitType.ATTACHMENT:
                return ['attachment-units', lectureUnit.id, 'edit'];
            case LectureUnitType.VIDEO:
                return ['video-units', lectureUnit.id, 'edit'];
            case LectureUnitType.TEXT:
                return ['text-units', lectureUnit.id, 'edit'];
            case LectureUnitType.ONLINE:
                return ['online-units', lectureUnit.id, 'edit'];
            default:
                return;
        }
    }

    getLectureUnitReleaseDate(lectureUnit: LectureUnit) {
        switch (lectureUnit.type) {
            case LectureUnitType.ATTACHMENT:
                return (<AttachmentUnit>lectureUnit)?.attachment?.releaseDate || undefined;
            case LectureUnitType.EXERCISE:
                return (<ExerciseUnit>lectureUnit)?.exercise?.releaseDate || undefined;
            default:
                return lectureUnit.releaseDate || undefined;
        }
    }

    getAttachmentVersion(lectureUnit: LectureUnit) {
        switch (lectureUnit.type) {
            case LectureUnitType.ATTACHMENT:
                return (<AttachmentUnit>lectureUnit)?.attachment?.version || undefined;
            default:
                return undefined;
        }
    }
}
