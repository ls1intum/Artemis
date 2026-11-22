import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import dayjs from 'dayjs/esm';
import { AttachmentVideoUnitService, withFileName } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { onError } from 'app/foundation/util/global.utils';
import { AlertService } from 'app/foundation/service/alert.service';
import { AttachmentVideoUnitFormComponent, AttachmentVideoUnitFormData } from 'app/lecture/manage/lecture-units/attachment-video-unit-form/attachment-video-unit-form.component';
import { Observable, Subscription, combineLatest, tap } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiButtonDirective, TumAetUiConfirmDialogComponent, TumAetUiConfirmationService } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { RecordingRecoveryScope, TumLiveSaveRecoveryComponent } from '../tum-live-upload/tum-live-save-recovery.component';
import {
    RecordingLeaveComponent,
    RecordingRedirect,
    RecordingTarget,
    confirmRecordingLeave,
    createOutcomeIsUnknown,
    injectRecordingAvailability,
    unknownCreateDetail,
} from '../tum-live-upload/tum-live-creation';
import { finalize } from 'rxjs/operators';
import { LectureUnitLayoutComponent } from '../lecture-unit-layout/lecture-unit-layout.component';

@Component({
    selector: 'jhi-create-attachment-video-unit',
    templateUrl: './create-attachment-video-unit.component.html',
    imports: [
        LectureUnitLayoutComponent,
        AttachmentVideoUnitFormComponent,
        TumAetUiButtonDirective,
        TumAetUiConfirmDialogComponent,
        TumLiveSaveRecoveryComponent,
        ArtemisTranslatePipe,
    ],
    providers: [TumAetUiConfirmationService],
    host: { '(window:beforeunload)': 'onBeforeUnload($event)' },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateAttachmentVideoUnitComponent implements OnInit, RecordingLeaveComponent {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private attachmentVideoUnitService = inject(AttachmentVideoUnitService);
    private alertService = inject(AlertService);
    private destroyRef = inject(DestroyRef);
    private readonly confirmation = inject(TumAetUiConfirmationService);
    private readonly translate = inject(TranslateService);
    private readonly redirect = new RecordingRedirect();

    readonly attachmentVideoUnitForm = viewChild<AttachmentVideoUnitFormComponent>('attachmentVideoUnitForm');
    attachmentVideoUnitToCreate: AttachmentVideoUnit = new AttachmentVideoUnit();
    attachmentToCreate: Attachment = new Attachment();

    readonly isLoading = signal(false);
    readonly lectureId = signal<number | undefined>(undefined);
    readonly courseId = signal<number | undefined>(undefined);
    protected readonly recordingAvailable = injectRecordingAvailability(this.courseId);
    protected readonly savingRecording = signal(false);
    protected readonly savedRecording = signal<RecordingTarget | undefined>(undefined);
    protected readonly recovery = signal<RecordingRecoveryScope | undefined>(undefined);
    private recordingRequest?: Subscription;
    private recording?: File;
    private createdUnit?: AttachmentVideoUnit;

    ngOnInit() {
        const lectureRoute = this.activatedRoute.parent?.parent;
        const courseRoute = lectureRoute?.parent;
        if (!lectureRoute || !courseRoute) {
            return;
        }
        combineLatest([lectureRoute.paramMap, courseRoute.paramMap])
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(([params, parentParams]) => {
                const lectureIdParam = params.get('lectureId');
                const courseIdParam = parentParams.get('courseId');
                this.lectureId.set(lectureIdParam ? Number(lectureIdParam) : undefined);
                this.courseId.set(courseIdParam ? Number(courseIdParam) : undefined);
                const courseId = this.courseId();
                const lectureId = this.lectureId();
                if (!courseId || !lectureId) {
                    return;
                }
                const recovery = this.redirect.recoverPendingSave(courseId, lectureId);
                this.recovery.set(recovery);
                if (recovery) {
                    return;
                }
                const checkpoint = this.redirect.recover(courseId, lectureId);
                if (checkpoint?.unitId) {
                    this.savedRecording.set(checkpoint);
                    this.continueRecording();
                }
            });
        this.attachmentVideoUnitToCreate = new AttachmentVideoUnit();
        this.attachmentToCreate = new Attachment();
    }

    createAttachmentVideoUnit(attachmentVideoUnitFormData: AttachmentVideoUnitFormData): void {
        const { name, videoSource, description, releaseDate, competencyLinks } = attachmentVideoUnitFormData?.formProperties || {};
        const { file, fileName } = attachmentVideoUnitFormData?.fileProperties || {};

        const lectureId = this.lectureId();
        const courseId = this.courseId();
        // A unit may start without a file and a video, so its video can be uploaded to TUM.Live once it exists.
        const recording = attachmentVideoUnitFormData.recording;
        if (!name || lectureId === undefined || this.isLoading() || this.savedRecording() || this.recovery() || (recording && courseId === undefined)) {
            return;
        }

        // === Setting attachment ===
        this.attachmentToCreate.name = name;
        this.attachmentToCreate.releaseDate = releaseDate;
        this.attachmentToCreate.attachmentType = AttachmentType.FILE;
        this.attachmentToCreate.version = 1;
        this.attachmentToCreate.uploadDate = dayjs();

        // === Setting attachmentVideoUnit ===
        this.attachmentVideoUnitToCreate.name = name;
        this.attachmentVideoUnitToCreate.releaseDate = releaseDate;
        this.attachmentVideoUnitToCreate.description = description;
        this.attachmentVideoUnitToCreate.videoSource = recording ? undefined : videoSource;
        this.attachmentVideoUnitToCreate.competencyLinks = competencyLinks || [];

        this.isLoading.set(true);
        if (recording) {
            this.redirect.checkpointPendingSave({ courseId: courseId!, lectureId });
            this.savingRecording.set(true);
        }

        const upload = !!file && !!fileName;
        const creation = this.attachmentVideoUnitService.create(
            lectureId,
            this.attachmentVideoUnitToCreate,
            upload ? this.attachmentToCreate : undefined,
            upload ? withFileName(file, fileName) : undefined,
        );
        const request = (recording ? creation.pipe(takeUntilDestroyed(this.destroyRef)) : creation)
            .pipe(
                finalize(() => {
                    this.isLoading.set(false);
                    this.savingRecording.set(false);
                }),
            )
            .subscribe({
                next: (created) => {
                    if (!recording) {
                        if (!this.destroyRef.destroyed) {
                            void this.router.navigate(['../../'], { relativeTo: this.activatedRoute });
                        }
                        return;
                    }
                    if (created.id === undefined) {
                        this.markSaveUnknown();
                        return;
                    }
                    const target = { courseId: courseId!, lectureId, unitId: created.id };
                    this.recording = recording;
                    this.createdUnit = created;
                    this.savedRecording.set(target);
                    this.redirect.checkpoint(target);
                    this.continueRecording();
                },
                error: (res: HttpErrorResponse | Error) => {
                    if (recording) {
                        if (createOutcomeIsUnknown(res)) {
                            this.markSaveUnknown(res);
                            return;
                        }
                        this.redirect.checkpoint();
                    }
                    if (res instanceof Error) {
                        this.alertService.error(res.message);
                        return;
                    }
                    if (res.error?.params === 'file' && res?.error?.title) {
                        this.alertService.error(res.error.title);
                    } else {
                        onError(this.alertService, res);
                    }
                },
            });
        if (recording) {
            this.recordingRequest = request;
        }
    }

    protected continueRecording(): void {
        const target = this.savedRecording();
        if (target) {
            void this.redirect.open(target, true, this.recording, this.createdUnit);
        }
    }

    protected onBeforeUnload(event: BeforeUnloadEvent): void {
        if (this.savingRecording()) {
            event.preventDefault();
        }
    }

    confirmRecordingLeave(nextUrl?: string): Observable<boolean> | boolean {
        if (this.redirect.allows(nextUrl) || !this.savingRecording()) {
            return true;
        }
        return confirmRecordingLeave(this.confirmation, this.translate, 'saving').pipe(
            tap((leave) => {
                if (leave && this.savingRecording()) {
                    this.markSaveUnknown();
                    this.recordingRequest?.unsubscribe();
                }
            }),
        );
    }

    private markSaveUnknown(error?: unknown): void {
        this.recovery.set({ courseId: this.courseId()!, lectureId: this.lectureId(), detail: unknownCreateDetail(error) });
    }
}
