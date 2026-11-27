import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { onError } from 'app/foundation/util/global.utils';
import { ActivatedRoute, Router } from '@angular/router';
import { filter, finalize, switchMap, take } from 'rxjs/operators';
import { AttachmentVideoUnitService, withFileName } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { AttachmentUpdateIntent, AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/foundation/service/alert.service';
import { AttachmentVideoUnitFormComponent, AttachmentVideoUnitFormData } from 'app/lecture/manage/lecture-units/attachment-video-unit-form/attachment-video-unit-form.component';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { Observable, combineLatest, of, tap } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiConfirmDialogComponent, TumAetUiConfirmationService } from '@tumaet/ui-angular/confirm-dialog';
import { TumAetUiMessageComponent } from '@tumaet/ui-angular/message';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { RecordingLeaveComponent, confirmRecordingLeave, confirmReplacingRecording, recordingNavigation } from '../tum-live-upload/tum-live-creation';
import { LectureUnitLayoutComponent } from '../lecture-unit-layout/lecture-unit-layout.component';
import { cloneWith, hydrate } from 'app/foundation/util/deep-clone.util';
import { TumLiveUploadComponent } from 'app/lecture/manage/lecture-units/tum-live-upload/tum-live-upload.component';

@Component({
    selector: 'jhi-edit-attachment-video-unit',
    templateUrl: './edit-attachment-video-unit.component.html',
    imports: [LectureUnitLayoutComponent, AttachmentVideoUnitFormComponent, TumLiveUploadComponent, TumAetUiConfirmDialogComponent, TumAetUiMessageComponent, ArtemisTranslatePipe],
    providers: [TumAetUiConfirmationService],
    host: { '(window:beforeunload)': 'onBeforeUnload($event)' },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditAttachmentVideoUnitComponent implements OnInit, RecordingLeaveComponent {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private attachmentVideoUnitService = inject(AttachmentVideoUnitService);
    private alertService = inject(AlertService);
    private destroyRef = inject(DestroyRef);
    private readonly confirmation = inject(TumAetUiConfirmationService);
    private readonly translate = inject(TranslateService);
    private readonly navigationInfo = this.router.currentNavigation()?.extras.info;

    readonly attachmentVideoUnitForm = viewChild<AttachmentVideoUnitFormComponent>('attachmentVideoUnitForm');

    readonly isLoading = signal(false);
    readonly attachmentVideoUnit = signal<AttachmentVideoUnit | undefined>(undefined);
    readonly attachment = signal<Attachment | undefined>(undefined);
    readonly formData = signal<AttachmentVideoUnitFormData | undefined>(undefined);
    readonly lectureId = signal<number | undefined>(undefined);
    readonly notificationText = signal<string | undefined>(undefined);
    protected readonly upload = viewChild(TumLiveUploadComponent);
    protected readonly initialRecording = signal<File | undefined>(undefined);
    protected readonly needsReselection = signal(false);

    ngOnInit(): void {
        this.isLoading.set(true);
        const lectureRoute = this.activatedRoute.parent?.parent;
        if (!lectureRoute) {
            this.isLoading.set(false);
            return;
        }
        combineLatest([this.activatedRoute.paramMap, lectureRoute.paramMap])
            .pipe(
                take(1),
                filter(([params, parentParams]) => {
                    const hasUnitId = params.get('attachmentVideoUnitId') !== null;
                    const hasLectureId = parentParams.get('lectureId') !== null;
                    return hasUnitId && hasLectureId;
                }),
                switchMap(([params, parentParams]) => {
                    const attachmentVideoUnitId = Number(params.get('attachmentVideoUnitId'));
                    const lectureId = Number(parentParams.get('lectureId'));
                    this.lectureId.set(lectureId);
                    const courseId = Number(lectureRoute.parent?.snapshot.paramMap.get('courseId'));
                    const recording = recordingNavigation(this.navigationInfo, courseId, lectureId);
                    if (recording?.target.unitId === attachmentVideoUnitId) {
                        const file = recording.takeFile();
                        this.initialRecording.set(file);
                        this.needsReselection.set(!file);
                        if (recording.unit?.id === attachmentVideoUnitId) {
                            return of(recording.unit);
                        }
                    }
                    return this.attachmentVideoUnitService.findById(attachmentVideoUnitId, lectureId);
                }),
                takeUntilDestroyed(this.destroyRef),
                finalize(() => this.isLoading.set(false)),
            )
            .subscribe({
                next: (unit: AttachmentVideoUnit) => {
                    const attach = unit.attachment || {};
                    // breaking the connection to prevent errors in deserialization. will be reconnected on the server side
                    unit.attachment = undefined;
                    attach.attachmentVideoUnit = undefined;

                    this.attachmentVideoUnit.set(unit);
                    this.attachment.set(attach);
                    this.formData.set({
                        formProperties: {
                            name: unit.name,
                            description: unit.description,
                            releaseDate: unit.releaseDate,
                            version: attach.version,
                            competencyLinks: unit.competencyLinks,
                            videoSource: unit.videoSource,
                        },
                        fileProperties: {
                            fileName: attach.link,
                        },
                    });
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    protected onBeforeUnload(event: BeforeUnloadEvent): void {
        if (this.upload()?.transferring()) {
            event.preventDefault();
        }
    }

    confirmRecordingLeave(): Observable<boolean> | boolean {
        const upload = this.upload();
        if (!upload?.transferring()) {
            return true;
        }
        const phase = upload.transferProgress() === 100 ? 'sent' : 'sending';
        return confirmRecordingLeave(this.confirmation, this.translate, phase).pipe(
            tap((leave) => {
                if (leave && upload.transferring()) {
                    upload.cancel();
                }
            }),
        );
    }

    /**
     * Shows the video link of a video uploaded to TUM.Live, which Artemis set on the unit, so saving the form keeps it.
     * @param unitId the unit
     */
    onTumLiveVideoAttached(unitId: number): void {
        const lectureId = this.lectureId();
        if (lectureId === undefined) {
            return;
        }
        this.attachmentVideoUnitService
            .findById(unitId, lectureId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (response: AttachmentVideoUnit) => {
                    const videoSource = response.videoSource;
                    const unit = this.attachmentVideoUnit();
                    if (unit) {
                        this.attachmentVideoUnit.set(cloneWith(unit, { videoSource }));
                    }
                    this.attachmentVideoUnitForm()?.applyServerVideoSource(videoSource);
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateAttachmentVideoUnit(data: AttachmentVideoUnitFormData): void {
        const upload = this.upload();
        const loadedVideoSource = this.attachmentVideoUnit()?.videoSource || undefined;
        let decision: Observable<boolean> | undefined;
        if (upload?.transferring()) {
            // Saving leaves this editor, which would abort a transfer. Ask before sending any changes.
            const leave = this.confirmRecordingLeave();
            if (typeof leave !== 'boolean') {
                decision = leave;
            }
        } else if (upload?.pendingRef() && (data.formProperties.videoSource || undefined) !== loadedVideoSource) {
            decision = confirmReplacingRecording(this.confirmation, this.translate);
        }

        if (decision) {
            decision.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((accepted) => {
                if (accepted) {
                    this.saveAttachmentVideoUnit(data, loadedVideoSource);
                }
            });
        } else {
            this.saveAttachmentVideoUnit(data, loadedVideoSource);
        }
    }

    private saveAttachmentVideoUnit(attachmentVideoUnitFormData: AttachmentVideoUnitFormData, loadedVideoSource: string | undefined): void {
        if (this.isLoading()) {
            return;
        }
        const { description, name, releaseDate, updateNotificationText, videoSource, competencyLinks } = attachmentVideoUnitFormData.formProperties;
        const { file, fileName } = attachmentVideoUnitFormData.fileProperties;

        // optional update notification text for students
        if (updateNotificationText) {
            this.notificationText.set(updateNotificationText);
        }

        const currentAttachment = this.attachment();
        const currentUnit = this.attachmentVideoUnit();
        const lectureId = this.lectureId();

        if (!currentAttachment || !currentUnit || lectureId === undefined || currentUnit.id === undefined) {
            return;
        }

        // Create new objects to avoid mutating signal-stored objects
        const updatedAttachment = hydrate(new Attachment(), currentAttachment, {
            name,
            releaseDate,
            attachmentType: AttachmentType.FILE,
        });

        const hasUpload = !!file && file.size > 0;
        const updatedUnit = hydrate(new AttachmentVideoUnit(), currentUnit, {
            name,
            description,
            releaseDate,
            competencyLinks,
            videoSource,
            // An empty loaded link is sent as '': without the field the server compares with the link the unit has now.
            loadedVideoSource: loadedVideoSource ?? '',
            attachmentUpdateIntent: hasUpload ? AttachmentUpdateIntent.FILE_UPLOAD : AttachmentUpdateIntent.NO_FILE_CHANGE,
        });

        this.isLoading.set(true);

        // A video-only unit has no attachment to update until a file is uploaded.
        const sendsAttachment = currentAttachment.id !== undefined || hasUpload;

        this.attachmentVideoUnitService
            .update(lectureId, currentUnit.id, updatedUnit, {
                attachment: sendsAttachment ? updatedAttachment : undefined,
                file: hasUpload ? withFileName(file, fileName) : undefined,
                notificationText: this.notificationText() || undefined,
            })
            .pipe(
                takeUntilDestroyed(this.destroyRef),
                finalize(() => this.isLoading.set(false)),
            )
            .subscribe({
                next: (response: AttachmentVideoUnit) => {
                    const savedVideoSource = response.videoSource || undefined;
                    const sentVideoSource = videoSource || undefined;
                    if (sentVideoSource !== loadedVideoSource && savedVideoSource !== sentVideoSource) {
                        // The link was changed meanwhile, so the server kept that one. The page stays, shows it as the saved link and keeps the entered one to
                        // be saved again; the rest of the change is saved.
                        this.attachmentVideoUnit.set(cloneWith(currentUnit, { name, description, releaseDate, competencyLinks, videoSource: savedVideoSource }));
                        if (response.attachment) {
                            this.attachment.set(cloneWith(response.attachment, { attachmentVideoUnit: undefined }));
                        }
                        this.attachmentVideoUnitForm()?.applyServerVideoSource(savedVideoSource);
                        this.alertService.warning('artemisApp.lecture.unitEditor.videoSourceChangedMeanwhile');
                        return;
                    }
                    void this.router.navigate(['../../../'], { relativeTo: this.activatedRoute });
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
