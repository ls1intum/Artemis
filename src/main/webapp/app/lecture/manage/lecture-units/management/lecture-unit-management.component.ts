import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { finalize, map } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { LectureUnit, LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { AlertService } from 'app/shared/service/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { Subject } from 'rxjs';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { AttachmentVideoUnit, TranscriptionStatus } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { ExerciseUnit } from 'app/lecture/shared/entities/lecture-unit/exerciseUnit.model';
import { faBan, faExclamationTriangle, faEye, faFileLines, faPencilAlt, faRepeat, faSpinner, faTrash } from '@fortawesome/free-solid-svg-icons';
import { CdkDrag, CdkDragDrop, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { LectureTranscriptionService } from 'app/lecture/manage/services/lecture-transcription.service';
import { UnitCreationCardComponent } from '../unit-creation-card/unit-creation-card.component';
import { AttachmentVideoUnitComponent } from 'app/lecture/overview/course-lectures/attachment-video-unit/attachment-video-unit.component';
import { ExerciseUnitComponent } from 'app/lecture/overview/course-lectures/exercise-unit/exercise-unit.component';
import { TextUnitComponent } from 'app/lecture/overview/course-lectures/text-unit/text-unit.component';
import { OnlineUnitComponent } from 'app/lecture/overview/course-lectures/online-unit/online-unit.component';
import { CompetenciesPopoverComponent } from 'app/atlas/shared/competencies-popover/competencies-popover.component';
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
    protected readonly faSpinner = faSpinner;
    protected readonly faFileLines = faFileLines;
    protected readonly faExclamationTriangle = faExclamationTriangle;
    protected readonly faBan = faBan;
    protected readonly faRepeat = faRepeat;

    protected readonly LectureUnitType = LectureUnitType;
    protected readonly ActionType = ActionType;
    protected readonly TranscriptionStatus = TranscriptionStatus;

    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly lectureService = inject(LectureService);
    private readonly alertService = inject(AlertService);
    protected readonly lectureUnitService = inject(LectureUnitService);
    private readonly lectureTranscriptionService = inject(LectureTranscriptionService);

    @Input() showCreationCard = true;
    @Input() showCompetencies = true;
    @Input() emitEditEvents = false;
    @Input() lectureId: number | undefined;

    @Output() onEditLectureUnitClicked: EventEmitter<LectureUnit> = new EventEmitter<LectureUnit>();

    lectureUnits: LectureUnit[] = [];
    lecture: Lecture;
    isLoading = false;
    viewButtonAvailable: Record<number, boolean> = {};
    transcriptionStatus: Record<number, TranscriptionStatus> = {};
    isCancellationLoading: Record<number, boolean> = {};
    isRetryingProcessing: Record<number, boolean> = {};

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    routerEditLinksBase: { [key: string]: string } = {
        [LectureUnitType.ATTACHMENT_VIDEO]: 'attachment-video-units',
        [LectureUnitType.TEXT]: 'text-units',
        [LectureUnitType.ONLINE]: 'online-units',
    };

    ngOnInit(): void {
        this.lectureId = Number(this.activatedRoute?.parent?.snapshot.paramMap.get('lectureId'));
        if (this.lectureId) {
            // TODO: the lecture (without units) is already available through the lecture.route.ts resolver, it's not really good that we load it twice
            // ideally the router could load the details directly
            this.loadData();
        }
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
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
                            if (lectureUnit.type === LectureUnitType.ATTACHMENT_VIDEO) {
                                this.loadTranscriptionStatus(lectureUnit.id!);
                            }
                        });
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

    drop(event: CdkDragDrop<LectureUnit[]>) {
        moveItemInArray(this.lectureUnits, event.previousIndex, event.currentIndex);
        this.updateOrder();
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

    protected readonly AttachmentVideoUnit = AttachmentVideoUnit;

    private loadTranscriptionStatus(lectureUnitId: number) {
        this.lectureTranscriptionService.getTranscriptionStatus(lectureUnitId).subscribe({
            next: (status) => {
                if (status) {
                    this.transcriptionStatus[lectureUnitId] = status;
                }
            },
        });
    }

    cancelTranscription(lectureUnit: AttachmentVideoUnit) {
        const lectureUnitId = lectureUnit.id;
        if (!lectureUnitId || this.isCancellationLoading[lectureUnitId]) {
            return;
        }

        this.isCancellationLoading[lectureUnitId] = true;
        this.lectureTranscriptionService
            .cancelTranscription(lectureUnitId)
            .pipe(finalize(() => (this.isCancellationLoading[lectureUnitId] = false)))
            .subscribe({
                next: (success) => {
                    if (success) {
                        this.alertService.success('artemisApp.attachmentVideoUnit.transcription.cancelSuccess');
                        delete this.transcriptionStatus[lectureUnitId];
                    } else {
                        this.alertService.error('artemisApp.attachmentVideoUnit.transcription.cancelError');
                    }
                },
                error: () => {
                    this.alertService.error('artemisApp.attachmentVideoUnit.transcription.cancelError');
                },
            });
    }

    hasTranscription(lectureUnit: AttachmentVideoUnit): boolean {
        return this.transcriptionStatus[lectureUnit.id!] === TranscriptionStatus.COMPLETED;
    }

    isTranscriptionPending(lectureUnit: AttachmentVideoUnit): boolean {
        const status = this.transcriptionStatus[lectureUnit.id!];
        return status === TranscriptionStatus.PENDING || status === TranscriptionStatus.PROCESSING;
    }

    isTranscriptionFailed(lectureUnit: AttachmentVideoUnit): boolean {
        return this.transcriptionStatus[lectureUnit.id!] === TranscriptionStatus.FAILED;
    }

    hasTranscriptionBadge(lectureUnit: AttachmentVideoUnit): boolean {
        return this.isTranscriptionPending(lectureUnit) || this.isTranscriptionFailed(lectureUnit) || this.hasTranscription(lectureUnit);
    }

    getBadgeTopOffset(lectureUnit: LectureUnit) {
        let offset = 0;
        if (lectureUnit.type === LectureUnitType.ATTACHMENT_VIDEO) {
            const hasAttachment = this.hasAttachment(<AttachmentVideoUnit>lectureUnit);
            const hasTranscriptionBadge = this.hasTranscriptionBadge(<AttachmentVideoUnit>lectureUnit);
            if (hasAttachment && hasTranscriptionBadge) {
                offset = -60;
            } else if (hasTranscriptionBadge || hasAttachment) {
                offset = -40;
            }
        }
        return offset === 0 ? null : `${offset}px`;
    }

    /**
     * Retry processing for a failed lecture unit.
     * @param lectureUnit the lecture unit to retry processing for
     */
    retryProcessing(lectureUnit: AttachmentVideoUnit) {
        if (!this.lectureId || !lectureUnit.id) {
            return;
        }

        this.isRetryingProcessing[lectureUnit.id] = true;
        this.lectureUnitService.retryProcessing(this.lectureId, lectureUnit.id).subscribe({
            next: () => {
                this.alertService.success('artemisApp.lectureUnit.processingRetryStarted');
                // Reload transcription status after a short delay to show updated state
                setTimeout(() => {
                    this.loadTranscriptionStatus(lectureUnit.id!);
                    this.isRetryingProcessing[lectureUnit.id!] = false;
                }, 1000);
            },
            error: (errorResponse: HttpErrorResponse) => {
                onError(this.alertService, errorResponse);
                this.isRetryingProcessing[lectureUnit.id!] = false;
            },
        });
    }
}
