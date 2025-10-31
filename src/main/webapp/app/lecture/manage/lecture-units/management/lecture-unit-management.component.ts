import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, inject } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router, RouterLink } from '@angular/router';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { debounceTime, filter, finalize, map } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { LectureUnit, LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { AlertService } from 'app/shared/service/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { Subject, Subscription } from 'rxjs';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { AttachmentVideoUnit, IngestionState } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { ExerciseUnit } from 'app/lecture/shared/entities/lecture-unit/exerciseUnit.model';
import { IconDefinition, faCheckCircle, faEye, faFileExport, faPencilAlt, faRepeat, faSpinner, faTrash } from '@fortawesome/free-solid-svg-icons';
import { CdkDrag, CdkDragDrop, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';
import { PROFILE_IRIS } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { UnitCreationCardComponent } from '../unit-creation-card/unit-creation-card.component';
import { AttachmentVideoUnitComponent } from 'app/lecture/overview/course-lectures/attachment-video-unit/attachment-video-unit.component';
import { ExerciseUnitComponent } from 'app/lecture/overview/course-lectures/exercise-unit/exercise-unit.component';
import { TextUnitComponent } from 'app/lecture/overview/course-lectures/text-unit/text-unit.component';
import { OnlineUnitComponent } from 'app/lecture/overview/course-lectures/online-unit/online-unit.component';
import { CompetenciesPopoverComponent } from 'app/atlas/shared/competencies-popover/competencies-popover.component';
import { NgClass } from '@angular/common';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-lecture-unit-management',
    templateUrl: './lecture-unit-management.component.html',
    styleUrls: ['./lecture-unit-management.component.scss'],
    imports: [
        TranslateDirective,
        UnitCreationCardComponent,
        CdkDropList,
        CdkDrag,
        AttachmentVideoUnitComponent,
        ExerciseUnitComponent,
        TextUnitComponent,
        OnlineUnitComponent,
        CompetenciesPopoverComponent,
        NgClass,
        NgbTooltip,
        FaIconComponent,
        RouterLink,
        DeleteButtonDirective,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
    ],
})
export class LectureUnitManagementComponent implements OnInit, OnDestroy {
    protected readonly faTrash = faTrash;
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faEye = faEye;
    protected readonly faFileExport = faFileExport;
    protected readonly faRepeat = faRepeat;
    protected readonly faCheckCircle = faCheckCircle;
    protected readonly faSpinner = faSpinner;

    protected readonly LectureUnitType = LectureUnitType;
    protected readonly ActionType = ActionType;

    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly lectureService = inject(LectureService);
    private readonly alertService = inject(AlertService);
    private readonly profileService = inject(ProfileService);
    private readonly irisSettingsService = inject(IrisSettingsService);
    protected readonly lectureUnitService = inject(LectureUnitService);

    @Input() showCreationCard = true;
    @Input() showCompetencies = true;
    @Input() emitEditEvents = false;

    @Input() lectureId: number | undefined;

    @Output()
    onEditLectureUnitClicked: EventEmitter<LectureUnit> = new EventEmitter<LectureUnit>();

    lectureUnits: LectureUnit[] = [];
    lecture: Lecture;
    isLoading = false;
    updateOrderSubject: Subject<any>;
    viewButtonAvailable: Record<number, boolean> = {};

    private updateOrderSubjectSubscription: Subscription;
    private navigationEndSubscription: Subscription;
    private activatedRouteSubscription?: Subscription;
    private profileInfoSubscription: Subscription;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    irisEnabled = false;
    lectureIngestionEnabled = false;
    routerEditLinksBase: { [key: string]: string } = {
        [LectureUnitType.ATTACHMENT_VIDEO]: 'attachment-video-units',
        [LectureUnitType.TEXT]: 'text-units',
        [LectureUnitType.ONLINE]: 'online-units',
    };

    ngOnInit(): void {
        this.navigationEndSubscription = this.router.events.pipe(filter((value) => value instanceof NavigationEnd)).subscribe(() => {
            this.loadData();
        });

        this.updateOrderSubject = new Subject();
        this.activatedRouteSubscription = this.activatedRoute?.parent?.params.subscribe((params) => {
            this.lectureId ??= +params['lectureId'];
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

    ngOnDestroy(): void {
        this.updateOrder();
        this.updateOrderSubjectSubscription.unsubscribe();
        this.dialogErrorSource.unsubscribe();
        this.navigationEndSubscription.unsubscribe();
        this.profileInfoSubscription?.unsubscribe();
        this.activatedRouteSubscription?.unsubscribe();
    }

    loadData() {
        this.isLoading = true;
        // TODO: we actually would like to have the lecture with all units! Posts and competencies are not required here
        // we could also simply load all units for the lecture (as the lecture is already available through the route, see TODO above)
        this.lectureService
            .findWithDetails(this.lectureId!)
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
                        this.lectureUnits.forEach((lectureUnit) => {
                            this.viewButtonAvailable[lectureUnit.id!] = this.isViewButtonAvailable(lectureUnit);
                        });
                        this.initializeProfileInfo();
                        if (this.lectureIngestionEnabled) {
                            this.updateIngestionStates();
                        }
                    } else {
                        this.lectureUnits = [];
                    }
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }

    updateOrder() {
        if (this.lectureId === undefined || isNaN(this.lectureId)) {
            return;
        }

        this.lectureUnitService
            .updateOrder(this.lectureId!, this.lectureUnits)
            .pipe(map((response: HttpResponse<LectureUnit[]>) => response.body!))
            .subscribe({
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }

    initializeProfileInfo() {
        this.irisEnabled = this.profileService.isProfileActive(PROFILE_IRIS);
        if (this.irisEnabled && this.lecture.course && this.lecture.course.id) {
            this.irisSettingsService.getCourseSettings(this.lecture.course.id).subscribe((response) => {
                this.lectureIngestionEnabled = response?.settings?.enabled || false;
            });
        }
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
            case LectureUnitType.ATTACHMENT_VIDEO:
                return 'artemisApp.attachmentVideoUnit.delete.question';
            case LectureUnitType.TEXT:
                return 'artemisApp.textUnit.delete.question';
            case LectureUnitType.ONLINE:
                return 'artemisApp.onlineUnit.delete.question';
            default:
                return '';
        }
    }

    getDeleteConfirmationTextKey(lectureUnit: LectureUnit) {
        switch (lectureUnit.type) {
            case LectureUnitType.EXERCISE:
                return 'artemisApp.exerciseUnit.delete.typeNameToConfirm';
            case LectureUnitType.ATTACHMENT_VIDEO:
                return 'artemisApp.attachmentVideoUnit.delete.typeNameToConfirm';
            case LectureUnitType.TEXT:
                return 'artemisApp.textUnit.delete.typeNameToConfirm';
            case LectureUnitType.ONLINE:
                return 'artemisApp.onlineUnit.delete.typeNameToConfirm';
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
        this.lectureUnitService.delete(lectureUnitId, this.lectureId!).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.loadData();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    isViewButtonAvailable(lectureUnit: LectureUnit): boolean {
        switch (lectureUnit!.type) {
            case LectureUnitType.ATTACHMENT_VIDEO: {
                const attachmentVideoUnit = <AttachmentVideoUnit>lectureUnit;
                return attachmentVideoUnit.attachment?.link?.endsWith('.pdf') ?? false;
            }
            default:
                return false;
        }
    }

    editButtonAvailable(lectureUnit: LectureUnit) {
        switch (lectureUnit?.type) {
            case LectureUnitType.ATTACHMENT_VIDEO:
            case LectureUnitType.TEXT:
            case LectureUnitType.ONLINE:
                return true;
            default:
                return false;
        }
    }

    onEditButtonClicked(lectureUnit: LectureUnit) {
        this.onEditLectureUnitClicked.emit(lectureUnit);
    }

    getLectureUnitReleaseDate(lectureUnit: LectureUnit) {
        switch (lectureUnit.type) {
            case LectureUnitType.EXERCISE:
                return (<ExerciseUnit>lectureUnit)?.exercise?.releaseDate || undefined;
            default:
                return lectureUnit.releaseDate || undefined;
        }
    }

    getAttachmentVersion(lectureUnit: LectureUnit) {
        switch (lectureUnit.type) {
            case LectureUnitType.ATTACHMENT_VIDEO:
                return (<AttachmentVideoUnit>lectureUnit)?.attachment?.version || undefined;
            default:
                return undefined;
        }
    }

    hasAttachment(lectureUnit: AttachmentVideoUnit): boolean {
        return !!lectureUnit.attachment;
    }

    /**
     * Fetches the ingestion state for each lecture unit asynchronously and updates the lecture unit object.
     */
    updateIngestionStates() {
        this.lectureUnitService.getIngestionState(this.lecture.course!.id!, this.lecture.id!).subscribe({
            next: (res: HttpResponse<Record<number, IngestionState>>) => {
                if (res.body) {
                    const ingestionStatesMap = res.body;
                    this.lectureUnits.forEach((lectureUnit) => {
                        if (lectureUnit.id) {
                            const ingestionState = ingestionStatesMap[lectureUnit.id];
                            if (ingestionState !== undefined) {
                                (<AttachmentVideoUnit>lectureUnit).pyrisIngestionState = ingestionState;
                            }
                        }
                    });
                }
            },
            error: () => {
                this.alertService.error('artemisApp.iris.ingestionAlert.pyrisError');
            },
        });
    }

    onIngestButtonClicked(lectureUnitId: number) {
        //TODO: ingest transcription as well
        const unitIndex: number = this.lectureUnits.findIndex((unit) => unit.id === lectureUnitId);
        if (unitIndex > -1) {
            const unit: AttachmentVideoUnit = this.lectureUnits[unitIndex];
            unit.pyrisIngestionState = IngestionState.IN_PROGRESS;
            this.lectureUnits[unitIndex] = unit;
        }
        this.lectureUnitService.ingestLectureUnitInPyris(lectureUnitId, this.lecture.id!).subscribe({
            next: () => this.alertService.success('artemisApp.iris.ingestionAlert.lectureUnitSuccess'),
            error: (error) => {
                if (error.status === 400) {
                    this.alertService.error('artemisApp.iris.ingestionAlert.lectureUnitError');
                } else if (error.status === 503) {
                    this.alertService.error('artemisApp.iris.ingestionAlert.pyrisUnavailable');
                } else {
                    this.alertService.error('artemisApp.iris.ingestionAlert.pyrisError');
                }
            },
        });
    }

    getIcon(attachmentVideoUnit: AttachmentVideoUnit): IconDefinition {
        switch (attachmentVideoUnit.pyrisIngestionState) {
            case IngestionState.NOT_STARTED:
                return this.faFileExport;
            case IngestionState.IN_PROGRESS:
                return this.faSpinner;
            case IngestionState.DONE:
                return this.faCheckCircle;
            case IngestionState.ERROR:
                return this.faRepeat;
            default:
                return this.faFileExport;
        }
    }

    protected readonly AttachmentVideoUnit = AttachmentVideoUnit;
}
