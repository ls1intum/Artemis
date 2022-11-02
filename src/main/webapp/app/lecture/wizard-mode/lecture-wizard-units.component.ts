import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { Lecture } from 'app/entities/lecture.model';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { OnlineUnit } from 'app/entities/lecture-unit/onlineUnit.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { TextUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/text-unit-form/text-unit-form.component';
import { VideoUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/video-unit-form/video-unit-form.component';
import { OnlineUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/online-unit-form/online-unit-form.component';
import { AttachmentUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/attachment-unit-form/attachment-unit-form.component';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { onError } from 'app/shared/util/global.utils';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { objectToJsonBlob } from 'app/utils/blob-util';
import { LectureUnitManagementComponent } from 'app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.component';
import { TextUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/textUnit.service';
import { VideoUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/videoUnit.service';
import { OnlineUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/onlineUnit.service';
import { AlertService } from 'app/core/util/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import dayjs from 'dayjs/esm';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-lecture-update-wizard-units',
    templateUrl: './lecture-wizard-units.component.html',
})
export class LectureUpdateWizardUnitsComponent implements OnInit {
    @Input() currentStep: number;
    @Input() lecture: Lecture;

    @ViewChild(LectureUnitManagementComponent, { static: false }) unitManagementComponent: LectureUnitManagementComponent;

    isEditingLectureUnit: boolean;
    isTextUnitFormOpen: boolean;
    isExerciseUnitFormOpen: boolean;
    isVideoUnitFormOpen: boolean;
    isOnlineUnitFormOpen: boolean;
    isAttachmentUnitFormOpen: boolean;

    currentlyProcessedTextUnit: TextUnit;
    currentlyProcessedVideoUnit: VideoUnit;
    currentlyProcessedOnlineUnit: OnlineUnit;
    currentlyProcessedAttachmentUnit: AttachmentUnit;
    textUnitFormData: TextUnitFormData;
    videoUnitFormData: VideoUnitFormData;
    onlineUnitFormData: OnlineUnitFormData;
    attachmentUnitFormData: AttachmentUnitFormData;

    constructor(
        protected activatedRoute: ActivatedRoute,
        protected alertService: AlertService,
        protected textUnitService: TextUnitService,
        protected videoUnitService: VideoUnitService,
        protected onlineUnitService: OnlineUnitService,
        protected attachmentUnitService: AttachmentUnitService,
    ) {}

    ngOnInit() {
        this.activatedRoute.queryParams.subscribe((params) => {
            // Checks if the exercise unit form should be opened initially, i.e. coming back from the exercise creation
            if (params.shouldOpenCreateExercise) {
                this.onCreateLectureUnit(LectureUnitType.EXERCISE);
            }
        });
    }

    onCreateLectureUnit(type: LectureUnitType) {
        this.isEditingLectureUnit = false;

        switch (type) {
            case LectureUnitType.TEXT:
                this.isTextUnitFormOpen = true;
                break;
            case LectureUnitType.EXERCISE:
                this.isExerciseUnitFormOpen = true;
                break;
            case LectureUnitType.VIDEO:
                this.isVideoUnitFormOpen = true;
                break;
            case LectureUnitType.ONLINE:
                this.isOnlineUnitFormOpen = true;
                break;
            case LectureUnitType.ATTACHMENT:
                this.isAttachmentUnitFormOpen = true;
                break;
        }
    }

    isAnyUnitFormOpen(): boolean {
        return this.isTextUnitFormOpen || this.isVideoUnitFormOpen || this.isOnlineUnitFormOpen || this.isAttachmentUnitFormOpen || this.isExerciseUnitFormOpen;
    }

    onCloseLectureUnitForms() {
        this.isTextUnitFormOpen = false;
        this.isVideoUnitFormOpen = false;
        this.isOnlineUnitFormOpen = false;
        this.isAttachmentUnitFormOpen = false;
        this.isExerciseUnitFormOpen = false;
    }

    createEditTextUnit(formData: TextUnitFormData) {
        if (!formData?.name) {
            return;
        }

        const { name, releaseDate, content } = formData;

        this.currentlyProcessedTextUnit = this.isEditingLectureUnit ? this.currentlyProcessedTextUnit : new TextUnit();
        this.currentlyProcessedTextUnit.name = name;
        this.currentlyProcessedTextUnit.releaseDate = releaseDate;
        this.currentlyProcessedTextUnit.content = content;

        (this.isEditingLectureUnit
            ? this.textUnitService.update(this.currentlyProcessedTextUnit, this.lecture.id!)
            : this.textUnitService.create(this.currentlyProcessedTextUnit!, this.lecture.id!)
        ).subscribe({
            next: () => {
                this.onCloseLectureUnitForms();
                this.unitManagementComponent.loadData();
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    createEditVideoUnit(formData: VideoUnitFormData) {
        if (!formData?.name || !formData?.source) {
            return;
        }

        const { name, description, releaseDate, source } = formData;

        this.currentlyProcessedVideoUnit = this.isEditingLectureUnit ? this.currentlyProcessedVideoUnit : new VideoUnit();
        this.currentlyProcessedVideoUnit.name = name || undefined;
        this.currentlyProcessedVideoUnit.releaseDate = releaseDate || undefined;
        this.currentlyProcessedVideoUnit.description = description || undefined;
        this.currentlyProcessedVideoUnit.source = source || undefined;

        (this.isEditingLectureUnit
            ? this.videoUnitService.update(this.currentlyProcessedVideoUnit, this.lecture.id!)
            : this.videoUnitService.create(this.currentlyProcessedVideoUnit!, this.lecture.id!)
        ).subscribe({
            next: () => {
                this.onCloseLectureUnitForms();
                this.unitManagementComponent.loadData();
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    createEditOnlineUnit(formData: OnlineUnitFormData) {
        if (!formData?.name || !formData?.source) {
            return;
        }

        const { name, description, releaseDate, source } = formData;

        this.currentlyProcessedOnlineUnit = this.isEditingLectureUnit ? this.currentlyProcessedOnlineUnit : new OnlineUnit();
        this.currentlyProcessedOnlineUnit.name = name || undefined;
        this.currentlyProcessedOnlineUnit.releaseDate = releaseDate || undefined;
        this.currentlyProcessedOnlineUnit.description = description || undefined;
        this.currentlyProcessedOnlineUnit.source = source || undefined;

        (this.isEditingLectureUnit
            ? this.onlineUnitService.update(this.currentlyProcessedOnlineUnit, this.lecture.id!)
            : this.onlineUnitService.create(this.currentlyProcessedOnlineUnit!, this.lecture.id!)
        ).subscribe({
            next: () => {
                this.onCloseLectureUnitForms();
                this.unitManagementComponent.loadData();
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    createEditAttachmentUnit(attachmentUnitFormData: AttachmentUnitFormData): void {
        if (!attachmentUnitFormData?.formProperties?.name || !attachmentUnitFormData?.fileProperties?.file || !attachmentUnitFormData?.fileProperties?.fileName) {
            return;
        }

        const { description, name, releaseDate, updateNotificationText } = attachmentUnitFormData.formProperties;
        const { file, fileName } = attachmentUnitFormData.fileProperties;

        this.currentlyProcessedAttachmentUnit = this.isEditingLectureUnit ? this.currentlyProcessedAttachmentUnit : new AttachmentUnit();
        const attachmentToCreateOrEdit = this.isEditingLectureUnit ? this.currentlyProcessedAttachmentUnit.attachment! : new Attachment();

        if (this.isEditingLectureUnit) {
            // breaking the connection to prevent errors in deserialization. will be reconnected on the server side
            this.currentlyProcessedAttachmentUnit.attachment = undefined;
            attachmentToCreateOrEdit.attachmentUnit = undefined;
        }

        let notificationText: string | undefined;

        if (updateNotificationText) {
            notificationText = updateNotificationText;
        }

        if (name) {
            attachmentToCreateOrEdit.name = name;
        }
        if (releaseDate) {
            attachmentToCreateOrEdit.releaseDate = releaseDate;
        }
        attachmentToCreateOrEdit.attachmentType = AttachmentType.FILE;
        attachmentToCreateOrEdit.version = 1;
        attachmentToCreateOrEdit.uploadDate = dayjs();

        if (description) {
            this.currentlyProcessedAttachmentUnit.description = description;
        }

        const formData = new FormData();
        formData.append('file', file, fileName);
        formData.append('attachment', objectToJsonBlob(attachmentToCreateOrEdit));
        formData.append('attachmentUnit', objectToJsonBlob(this.currentlyProcessedAttachmentUnit));

        (this.isEditingLectureUnit
            ? this.attachmentUnitService.update(this.lecture.id!, this.currentlyProcessedAttachmentUnit.id!, formData, notificationText)
            : this.attachmentUnitService.create(formData, this.lecture.id!)
        ).subscribe({
            next: () => {
                this.onCloseLectureUnitForms();
                this.unitManagementComponent.loadData();
            },
            error: (res: HttpErrorResponse) => {
                if (res.error?.params === 'file' && res?.error?.title) {
                    this.alertService.error(res.error.title);
                } else {
                    onError(this.alertService, res);
                }
            },
        });
    }

    /**
     * Called when all selected exercises were linked from the component
     */
    onExerciseUnitCreated() {
        this.onCloseLectureUnitForms();
        this.unitManagementComponent.loadData();
    }

    startEditLectureUnit(lectureUnit: LectureUnit) {
        this.isEditingLectureUnit = true;

        lectureUnit.lecture = new Lecture();
        lectureUnit.lecture.id = this.lecture.id;
        lectureUnit.lecture.course = this.lecture.course;

        switch (lectureUnit.type) {
            case LectureUnitType.TEXT:
                this.currentlyProcessedTextUnit = lectureUnit;
                this.isTextUnitFormOpen = true;
                this.isVideoUnitFormOpen = false;
                this.isExerciseUnitFormOpen = false;
                this.isOnlineUnitFormOpen = false;
                this.isAttachmentUnitFormOpen = false;
                this.textUnitFormData = {
                    name: this.currentlyProcessedTextUnit.name,
                    releaseDate: this.currentlyProcessedTextUnit.releaseDate,
                    content: this.currentlyProcessedTextUnit.content,
                };
                break;
            case LectureUnitType.VIDEO:
                this.currentlyProcessedVideoUnit = lectureUnit;
                this.isVideoUnitFormOpen = true;
                this.isExerciseUnitFormOpen = false;
                this.isOnlineUnitFormOpen = false;
                this.isAttachmentUnitFormOpen = false;
                this.isTextUnitFormOpen = false;
                this.videoUnitFormData = {
                    name: this.currentlyProcessedVideoUnit.name,
                    description: this.currentlyProcessedVideoUnit.description,
                    releaseDate: this.currentlyProcessedVideoUnit.releaseDate,
                    source: this.currentlyProcessedVideoUnit.source,
                };
                break;
            case LectureUnitType.ONLINE:
                this.currentlyProcessedOnlineUnit = lectureUnit;
                this.isOnlineUnitFormOpen = true;
                this.isAttachmentUnitFormOpen = false;
                this.isTextUnitFormOpen = false;
                this.isVideoUnitFormOpen = false;
                this.isExerciseUnitFormOpen = false;
                this.onlineUnitFormData = {
                    name: this.currentlyProcessedOnlineUnit.name,
                    description: this.currentlyProcessedOnlineUnit.description,
                    releaseDate: this.currentlyProcessedOnlineUnit.releaseDate,
                    source: this.currentlyProcessedOnlineUnit.source,
                };
                break;
            case LectureUnitType.ATTACHMENT:
                this.currentlyProcessedAttachmentUnit = lectureUnit;
                this.isAttachmentUnitFormOpen = true;
                this.isTextUnitFormOpen = false;
                this.isVideoUnitFormOpen = false;
                this.isExerciseUnitFormOpen = false;
                this.isOnlineUnitFormOpen = false;
                this.attachmentUnitFormData = {
                    formProperties: {
                        name: this.currentlyProcessedAttachmentUnit.attachment!.name,
                        description: this.currentlyProcessedAttachmentUnit.description,
                        releaseDate: this.currentlyProcessedAttachmentUnit.attachment!.releaseDate,
                        version: this.currentlyProcessedAttachmentUnit.attachment!.version,
                    },
                    fileProperties: {
                        fileName: this.currentlyProcessedAttachmentUnit.attachment!.link,
                    },
                };
                break;
        }
    }
}
