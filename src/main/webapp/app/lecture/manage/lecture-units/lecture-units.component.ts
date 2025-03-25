import { Component, Input, OnInit, ViewChild, computed, inject, signal, viewChild } from '@angular/core';
import { Lecture } from 'app/entities/lecture.model';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { OnlineUnit } from 'app/entities/lecture-unit/onlineUnit.model';
import { AttachmentVideoUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { TextUnitFormComponent, TextUnitFormData } from 'app/lecture/manage/lecture-units/text-unit-form/text-unit-form.component';
import { VideoUnitFormComponent, VideoUnitFormData } from 'app/lecture/manage/lecture-units/video-unit-form/video-unit-form.component';
import { OnlineUnitFormComponent, OnlineUnitFormData } from 'app/lecture/manage/lecture-units/online-unit-form/online-unit-form.component';
import { AttachmentVideoUnitFormComponent, AttachmentVideoUnitFormData } from 'app/lecture/manage/lecture-units/attachment-video-unit-form/attachment-video-unit-form.component';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { onError } from 'app/shared/util/global.utils';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { objectToJsonBlob } from 'app/shared/util/blob-util';
import { LectureUnitManagementComponent } from 'app/lecture/manage/lecture-units/lecture-unit-management.component';
import { TextUnitService } from 'app/lecture/manage/lecture-units/textUnit.service';
import { VideoUnitService } from 'app/lecture/manage/lecture-units/videoUnit.service';
import { OnlineUnitService } from 'app/lecture/manage/lecture-units/onlineUnit.service';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/attachment-video-unit.service';
import dayjs from 'dayjs/esm';
import { ActivatedRoute } from '@angular/router';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { UnitCreationCardComponent } from 'app/lecture/manage/lecture-units/unit-creation-card/unit-creation-card.component';
import { CreateExerciseUnitComponent } from 'app/lecture/manage/lecture-units/create-exercise-unit/create-exercise-unit.component';

@Component({
    selector: 'jhi-lecture-update-units',
    templateUrl: './lecture-units.component.html',
    imports: [
        TranslateDirective,
        LectureUnitManagementComponent,
        UnitCreationCardComponent,
        TextUnitFormComponent,
        VideoUnitFormComponent,
        OnlineUnitFormComponent,
        AttachmentVideoUnitFormComponent,
        CreateExerciseUnitComponent,
    ],
})
export class LectureUpdateUnitsComponent implements OnInit {
    protected activatedRoute = inject(ActivatedRoute);
    protected alertService = inject(AlertService);
    protected textUnitService = inject(TextUnitService);
    protected videoUnitService = inject(VideoUnitService);
    protected onlineUnitService = inject(OnlineUnitService);
    protected attachmentUnitService = inject(AttachmentVideoUnitService);

    @Input() lecture: Lecture;

    @ViewChild(LectureUnitManagementComponent, { static: false }) unitManagementComponent: LectureUnitManagementComponent;

    textUnitForm = viewChild(TextUnitFormComponent);
    videoUnitForm = viewChild(VideoUnitFormComponent);
    onlineUnitForm = viewChild(OnlineUnitFormComponent);
    attachmentUnitForm = viewChild(AttachmentVideoUnitFormComponent);
    isUnitConfigurationValid = computed(() => {
        return (
            (this.textUnitForm()?.isFormValid() || !this.isTextUnitFormOpen()) &&
            (this.videoUnitForm()?.isFormValid() || !this.isVideoUnitFormOpen()) &&
            (this.onlineUnitForm()?.isFormValid() || !this.isOnlineUnitFormOpen()) &&
            (this.attachmentUnitForm()?.isFormValid() || !this.isAttachmentUnitFormOpen())
        );
    });

    isEditingLectureUnit: boolean;
    isTextUnitFormOpen = signal<boolean>(false);
    isExerciseUnitFormOpen = signal<boolean>(false);
    isVideoUnitFormOpen = signal<boolean>(false);
    isOnlineUnitFormOpen = signal<boolean>(false);
    isAttachmentUnitFormOpen = signal<boolean>(false);

    currentlyProcessedTextUnit: TextUnit;
    currentlyProcessedVideoUnit: VideoUnit;
    currentlyProcessedOnlineUnit: OnlineUnit;
    currentlyProcessedAttachmentVideoUnit: AttachmentVideoUnit;
    textUnitFormData: TextUnitFormData;
    videoUnitFormData: VideoUnitFormData;
    onlineUnitFormData: OnlineUnitFormData;
    attachmentUnitFormData: AttachmentVideoUnitFormData;

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
        this.onCloseLectureUnitForms();
        switch (type) {
            case LectureUnitType.TEXT:
                this.isTextUnitFormOpen.set(true);
                break;
            case LectureUnitType.EXERCISE:
                this.isExerciseUnitFormOpen.set(true);
                break;
            case LectureUnitType.VIDEO:
                this.isVideoUnitFormOpen.set(true);
                break;
            case LectureUnitType.ONLINE:
                this.isOnlineUnitFormOpen.set(true);
                break;
            case LectureUnitType.ATTACHMENT:
                this.isAttachmentUnitFormOpen.set(true);
                break;
        }
    }

    isAnyUnitFormOpen = computed(() => {
        return this.isTextUnitFormOpen() || this.isVideoUnitFormOpen() || this.isOnlineUnitFormOpen() || this.isAttachmentUnitFormOpen() || this.isExerciseUnitFormOpen();
    });

    onCloseLectureUnitForms() {
        this.isTextUnitFormOpen.set(false);
        this.isVideoUnitFormOpen.set(false);
        this.isOnlineUnitFormOpen.set(false);
        this.isAttachmentUnitFormOpen.set(false);
        this.isExerciseUnitFormOpen.set(false);
    }

    createEditTextUnit(formData: TextUnitFormData) {
        if (!formData?.name) {
            return;
        }

        const { name, releaseDate, content, competencyLinks } = formData;

        this.currentlyProcessedTextUnit = this.isEditingLectureUnit ? this.currentlyProcessedTextUnit : new TextUnit();
        this.currentlyProcessedTextUnit.name = name;
        this.currentlyProcessedTextUnit.releaseDate = releaseDate;
        this.currentlyProcessedTextUnit.content = content;
        this.currentlyProcessedTextUnit.competencyLinks = competencyLinks;

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

        const { name, description, releaseDate, source, competencyLinks } = formData;

        this.currentlyProcessedVideoUnit = this.isEditingLectureUnit ? this.currentlyProcessedVideoUnit : new VideoUnit();
        this.currentlyProcessedVideoUnit.name = name || undefined;
        this.currentlyProcessedVideoUnit.releaseDate = releaseDate || undefined;
        this.currentlyProcessedVideoUnit.description = description || undefined;
        this.currentlyProcessedVideoUnit.source = source || undefined;
        this.currentlyProcessedVideoUnit.competencyLinks = competencyLinks;

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

        const { name, description, releaseDate, source, competencyLinks } = formData;

        this.currentlyProcessedOnlineUnit = this.isEditingLectureUnit ? this.currentlyProcessedOnlineUnit : new OnlineUnit();
        this.currentlyProcessedOnlineUnit.name = name || undefined;
        this.currentlyProcessedOnlineUnit.releaseDate = releaseDate || undefined;
        this.currentlyProcessedOnlineUnit.description = description || undefined;
        this.currentlyProcessedOnlineUnit.source = source || undefined;
        this.currentlyProcessedOnlineUnit.competencyLinks = competencyLinks || undefined;

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

    createEditAttachmentUnit(attachmentUnitFormData: AttachmentVideoUnitFormData): void {
        if (!attachmentUnitFormData?.formProperties?.name || !attachmentUnitFormData?.fileProperties?.file || !attachmentUnitFormData?.fileProperties?.fileName) {
            return;
        }

        const { description, name, releaseDate, updateNotificationText, competencyLinks } = attachmentUnitFormData.formProperties;
        const { file, fileName } = attachmentUnitFormData.fileProperties;

        this.currentlyProcessedAttachmentVideoUnit = this.isEditingLectureUnit ? this.currentlyProcessedAttachmentVideoUnit : new AttachmentVideoUnit();
        const attachmentToCreateOrEdit = this.isEditingLectureUnit ? this.currentlyProcessedAttachmentVideoUnit.attachment! : new Attachment();

        if (this.isEditingLectureUnit) {
            // breaking the connection to prevent errors in deserialization. will be reconnected on the server side
            this.currentlyProcessedAttachmentVideoUnit.attachment = undefined;
            attachmentToCreateOrEdit.attachmentVideoUnit = undefined;
        }

        let notificationText: string | undefined;

        if (updateNotificationText) {
            notificationText = updateNotificationText;
        }

        if (name) {
            this.currentlyProcessedAttachmentVideoUnit.name = name;
            attachmentToCreateOrEdit.name = name;
        }
        if (releaseDate) {
            attachmentToCreateOrEdit.releaseDate = releaseDate;
        }
        attachmentToCreateOrEdit.attachmentType = AttachmentType.FILE;
        attachmentToCreateOrEdit.version = 1;
        attachmentToCreateOrEdit.uploadDate = dayjs();

        if (description) {
            this.currentlyProcessedAttachmentVideoUnit.description = description;
        }
        this.currentlyProcessedAttachmentVideoUnit.competencyLinks = competencyLinks;

        const formData = new FormData();
        formData.append('file', file, fileName);
        formData.append('attachment', objectToJsonBlob(attachmentToCreateOrEdit));
        formData.append('attachmentVideoUnit', objectToJsonBlob(this.currentlyProcessedAttachmentVideoUnit));

        (this.isEditingLectureUnit
            ? this.attachmentUnitService.update(this.lecture.id!, this.currentlyProcessedAttachmentVideoUnit.id!, formData, notificationText)
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

        this.currentlyProcessedTextUnit = lectureUnit as TextUnit;
        this.currentlyProcessedVideoUnit = lectureUnit as VideoUnit;
        this.currentlyProcessedOnlineUnit = lectureUnit as OnlineUnit;
        this.currentlyProcessedAttachmentVideoUnit = lectureUnit as AttachmentVideoUnit;

        this.isTextUnitFormOpen.set(lectureUnit.type === LectureUnitType.TEXT);
        this.isVideoUnitFormOpen.set(lectureUnit.type === LectureUnitType.VIDEO);
        this.isExerciseUnitFormOpen.set(lectureUnit.type === LectureUnitType.EXERCISE);
        this.isOnlineUnitFormOpen.set(lectureUnit.type === LectureUnitType.ONLINE);
        this.isAttachmentUnitFormOpen.set(lectureUnit.type === LectureUnitType.ATTACHMENT);

        switch (lectureUnit.type) {
            case LectureUnitType.TEXT:
                this.textUnitFormData = {
                    name: this.currentlyProcessedTextUnit.name,
                    releaseDate: this.currentlyProcessedTextUnit.releaseDate,
                    content: this.currentlyProcessedTextUnit.content,
                };
                break;
            case LectureUnitType.VIDEO:
                this.videoUnitFormData = {
                    name: this.currentlyProcessedVideoUnit.name,
                    description: this.currentlyProcessedVideoUnit.description,
                    releaseDate: this.currentlyProcessedVideoUnit.releaseDate,
                    source: this.currentlyProcessedVideoUnit.source,
                };
                break;
            case LectureUnitType.ONLINE:
                this.onlineUnitFormData = {
                    name: this.currentlyProcessedOnlineUnit.name,
                    description: this.currentlyProcessedOnlineUnit.description,
                    releaseDate: this.currentlyProcessedOnlineUnit.releaseDate,
                    source: this.currentlyProcessedOnlineUnit.source,
                };
                break;
            case LectureUnitType.ATTACHMENT:
                this.attachmentUnitFormData = {
                    formProperties: {
                        name: this.currentlyProcessedAttachmentVideoUnit.attachment!.name,
                        description: this.currentlyProcessedAttachmentVideoUnit.description,
                        releaseDate: this.currentlyProcessedAttachmentVideoUnit.attachment!.releaseDate,
                        version: this.currentlyProcessedAttachmentVideoUnit.attachment!.version,
                        videoSource: this.currentlyProcessedAttachmentVideoUnit.videoSource,
                    },
                    fileProperties: {
                        fileName: this.currentlyProcessedAttachmentVideoUnit.attachment!.link,
                    },
                };
                break;
        }
    }
}
