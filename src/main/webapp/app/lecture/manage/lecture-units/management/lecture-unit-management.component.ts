import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { concatMap, finalize, map } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { LectureUnit, LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { AlertService } from 'app/shared/service/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { Subject, from } from 'rxjs';
import { LectureUnitProcessingStatus, LectureUnitService, ProcessingPhase } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { AttachmentVideoUnit, TranscriptionStatus } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { ExerciseUnit } from 'app/lecture/shared/entities/lecture-unit/exerciseUnit.model';
import { faClock, faExclamationTriangle, faEye, faFileLines, faPencilAlt, faRepeat, faSpinner, faTrash } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { CdkDrag, CdkDragDrop, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { LectureTranscriptionService } from 'app/lecture/manage/services/lecture-transcription.service';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
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
import { PdfDropZoneComponent } from '../../pdf-drop-zone/pdf-drop-zone.component';

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
        PdfDropZoneComponent,
    ],
})
export class LectureUnitManagementComponent implements OnInit, OnDestroy {
    protected readonly faTrash = faTrash;
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faEye = faEye;
    protected readonly faSpinner = faSpinner;
    protected readonly faFileLines = faFileLines;
    protected readonly faExclamationTriangle = faExclamationTriangle;
    protected readonly faRepeat = faRepeat;
    protected readonly faClock = faClock;

    protected readonly LectureUnitType = LectureUnitType;
    protected readonly ActionType = ActionType;
    protected readonly ProcessingPhase = ProcessingPhase;

    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly lectureService = inject(LectureService);
    private readonly alertService = inject(AlertService);
    protected readonly lectureUnitService = inject(LectureUnitService);
    private readonly lectureTranscriptionService = inject(LectureTranscriptionService);
    private readonly attachmentVideoUnitService = inject(AttachmentVideoUnitService);

    @Input() showCreationCard = true;
    @Input() showCompetencies = true;
    @Input() showDropZone = true;
    @Input() emitEditEvents = false;
    @Input() lectureId: number | undefined;

    @Output() onEditLectureUnitClicked: EventEmitter<LectureUnit> = new EventEmitter<LectureUnit>();

    lectureUnits: LectureUnit[] = [];
    lecture: Lecture;
    isLoading = false;
    viewButtonAvailable: Record<number, boolean> = {};
    transcriptionStatus: Record<number, TranscriptionStatus> = {};
    processingStatus: Record<number, LectureUnitProcessingStatus> = {};
    isRetryingProcessing: Record<number, boolean> = {};
    isUploadingPdfs = signal<boolean>(false);

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
                                this.loadProcessingStatus(lectureUnit.id!);
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

    private loadProcessingStatus(lectureUnitId: number) {
        if (!this.lectureId) {
            return;
        }
        this.lectureUnitService.getProcessingStatus(this.lectureId, lectureUnitId).subscribe({
            next: (status) => {
                if (status) {
                    this.processingStatus[lectureUnitId] = status;
                }
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

    // Processing status helper methods (for ProcessingPhase)
    isProcessingIdle(lectureUnit: AttachmentVideoUnit): boolean {
        return this.processingStatus[lectureUnit.id!]?.phase === ProcessingPhase.IDLE;
    }

    isProcessingTranscribing(lectureUnit: AttachmentVideoUnit): boolean {
        return this.processingStatus[lectureUnit.id!]?.phase === ProcessingPhase.TRANSCRIBING;
    }

    isProcessingIngesting(lectureUnit: AttachmentVideoUnit): boolean {
        return this.processingStatus[lectureUnit.id!]?.phase === ProcessingPhase.INGESTING;
    }

    isProcessingDone(lectureUnit: AttachmentVideoUnit): boolean {
        return this.processingStatus[lectureUnit.id!]?.phase === ProcessingPhase.DONE;
    }

    isProcessingFailed(lectureUnit: AttachmentVideoUnit): boolean {
        return this.processingStatus[lectureUnit.id!]?.phase === ProcessingPhase.FAILED;
    }

    isProcessingInProgress(lectureUnit: AttachmentVideoUnit): boolean {
        const phase = this.processingStatus[lectureUnit.id!]?.phase;
        return phase === ProcessingPhase.TRANSCRIBING || phase === ProcessingPhase.INGESTING;
    }

    getProcessingErrorKey(lectureUnit: AttachmentVideoUnit): string | undefined {
        return this.processingStatus[lectureUnit.id!]?.errorKey;
    }

    hasProcessingBadge(lectureUnit: AttachmentVideoUnit): boolean {
        return this.isProcessingInProgress(lectureUnit) || this.isProcessingFailed(lectureUnit) || this.isProcessingDone(lectureUnit) || this.isAwaitingProcessing(lectureUnit);
    }

    /**
     * Check if the course is currently active (within start and end dates).
     * A course is active if: startDate <= now <= endDate (null dates are treated as no restriction)
     */
    isCourseActive(): boolean {
        const course = this.lecture?.course;
        if (!course) {
            return false;
        }
        const now = dayjs();
        const startOk = !course.startDate || dayjs(course.startDate).isBefore(now) || dayjs(course.startDate).isSame(now);
        const endOk = !course.endDate || dayjs(course.endDate).isAfter(now) || dayjs(course.endDate).isSame(now);
        return startOk && endOk;
    }

    /**
     * Check if a lecture unit is awaiting processing (IDLE state and course is active so it will be processed).
     */
    isAwaitingProcessing(lectureUnit: AttachmentVideoUnit): boolean {
        const phase = this.processingStatus[lectureUnit.id!]?.phase;
        // If processing is in progress or done, it's not awaiting
        if (phase !== undefined && phase !== ProcessingPhase.IDLE) {
            return false;
        }
        // IDLE or no status yet - show "awaiting" only if the course is active (backfill scheduler only processes active courses)
        return this.isCourseActive();
    }

    getBadgeTopOffset(lectureUnit: LectureUnit) {
        let badgeCount = 1; // Release date badge is always there
        if (lectureUnit.type === LectureUnitType.ATTACHMENT_VIDEO) {
            if (this.hasTranscriptionBadge(<AttachmentVideoUnit>lectureUnit)) {
                badgeCount++;
            }
            if (this.hasProcessingBadge(<AttachmentVideoUnit>lectureUnit)) {
                badgeCount++;
            }
        }
        const offset = badgeCount > 1 ? -(badgeCount - 1) * 20 : 0;
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
                // Reload both statuses after a short delay to show updated state
                setTimeout(() => {
                    this.loadTranscriptionStatus(lectureUnit.id!);
                    this.loadProcessingStatus(lectureUnit.id!);
                    this.isRetryingProcessing[lectureUnit.id!] = false;
                }, 1000);
            },
            error: (errorResponse: HttpErrorResponse) => {
                onError(this.alertService, errorResponse);
                this.isRetryingProcessing[lectureUnit.id!] = false;
            },
        });
    }

    /**
     * Handles PDF files dropped on the drop zone
     * Creates attachment units for each file with name from filename and release date 15 min in future
     * Navigates to edit page for the last created unit
     */
    onPdfFilesDropped(files: File[]): void {
        if (files.length === 0 || !this.lectureId) {
            return;
        }

        this.isUploadingPdfs.set(true);
        let lastCreatedUnit: AttachmentVideoUnit | undefined;

        from(files)
            .pipe(
                concatMap((file) => this.createAttachmentUnitFromFile(file)),
                map((response) => response.body as AttachmentVideoUnit),
            )
            .subscribe({
                next: (unit) => {
                    lastCreatedUnit = unit;
                },
                error: (error: HttpErrorResponse) => {
                    this.isUploadingPdfs.set(false);
                    onError(this.alertService, error);
                },
                complete: () => {
                    this.isUploadingPdfs.set(false);
                    this.alertService.success('artemisApp.lecture.pdfUpload.success');
                    // Navigate to edit page for the last created unit
                    if (lastCreatedUnit?.id && this.lecture?.course?.id) {
                        this.router.navigate([
                            '/course-management',
                            this.lecture.course.id,
                            'lectures',
                            this.lectureId,
                            'unit-management',
                            'attachment-video-units',
                            lastCreatedUnit.id,
                            'edit',
                        ]);
                    } else {
                        this.loadData();
                    }
                },
            });
    }

    /**
     * Creates a single attachment unit from a file
     */
    private createAttachmentUnitFromFile(file: File) {
        return this.attachmentVideoUnitService.createAttachmentVideoUnitFromFile(this.lectureId!, file);
    }
}
