import { Component, ElementRef, Input, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { OnlineUnit } from 'app/lecture/shared/entities/lecture-unit/onlineUnit.model';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { TextUnitFormComponent, TextUnitFormData } from 'app/lecture/manage/lecture-units/text-unit-form/text-unit-form.component';
import { OnlineUnitFormComponent, OnlineUnitFormData } from 'app/lecture/manage/lecture-units/online-unit-form/online-unit-form.component';
import { AttachmentVideoUnitFormComponent, AttachmentVideoUnitFormData } from 'app/lecture/manage/lecture-units/attachment-video-unit-form/attachment-video-unit-form.component';
import { LectureUnit, LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { onError } from 'app/shared/util/global.utils';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { objectToJsonBlob } from 'app/shared/util/blob-util';
import { LectureUnitManagementComponent } from 'app/lecture/manage/lecture-units/management/lecture-unit-management.component';
import { TextUnitService } from 'app/lecture/manage/lecture-units/services/text-unit.service';
import { OnlineUnitService } from 'app/lecture/manage/lecture-units/services/online-unit.service';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import dayjs from 'dayjs/esm';
import { ActivatedRoute } from '@angular/router';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { UnitCreationCardComponent } from 'app/lecture/manage/lecture-units/unit-creation-card/unit-creation-card.component';
import { CreateExerciseUnitComponent } from 'app/lecture/manage/lecture-units/create-exercise-unit/create-exercise-unit.component';
import { concatMap, filter, map } from 'rxjs/operators';
import { from } from 'rxjs';
import { PdfDropZoneComponent } from '../pdf-drop-zone/pdf-drop-zone.component';

@Component({
    selector: 'jhi-lecture-update-units',
    templateUrl: './lecture-units.component.html',
    imports: [
        TranslateDirective,
        LectureUnitManagementComponent,
        UnitCreationCardComponent,
        TextUnitFormComponent,
        OnlineUnitFormComponent,
        AttachmentVideoUnitFormComponent,
        CreateExerciseUnitComponent,
        PdfDropZoneComponent,
    ],
})
export class LectureUpdateUnitsComponent implements OnInit {
    protected activatedRoute = inject(ActivatedRoute);
    protected alertService = inject(AlertService);
    protected textUnitService = inject(TextUnitService);
    protected onlineUnitService = inject(OnlineUnitService);
    protected attachmentVideoUnitService = inject(AttachmentVideoUnitService);

    @Input() lecture: Lecture;

    unitManagementComponent = viewChild(LectureUnitManagementComponent);
    editFormContainer = viewChild<ElementRef<HTMLElement>>('editFormContainer');

    textUnitForm = viewChild(TextUnitFormComponent);
    onlineUnitForm = viewChild(OnlineUnitFormComponent);
    attachmentVideoUnitForm = viewChild(AttachmentVideoUnitFormComponent);
    isUnitConfigurationValid = computed(() => {
        return (
            (this.textUnitForm()?.isFormValid() || !this.isTextUnitFormOpen()) &&
            (this.onlineUnitForm()?.isFormValid() || !this.isOnlineUnitFormOpen()) &&
            (this.attachmentVideoUnitForm()?.isFormValid() || !this.isAttachmentVideoUnitFormOpen())
        );
    });

    isEditingLectureUnit: boolean;
    isTextUnitFormOpen = signal<boolean>(false);
    isExerciseUnitFormOpen = signal<boolean>(false);
    isOnlineUnitFormOpen = signal<boolean>(false);
    isAttachmentVideoUnitFormOpen = signal<boolean>(false);
    isUploadingPdfs = signal<boolean>(false);

    currentlyProcessedTextUnit: TextUnit;
    currentlyProcessedOnlineUnit: OnlineUnit;
    currentlyProcessedAttachmentVideoUnit: AttachmentVideoUnit;
    textUnitFormData: TextUnitFormData;
    onlineUnitFormData: OnlineUnitFormData;
    attachmentVideoUnitFormData: AttachmentVideoUnitFormData;

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
            case LectureUnitType.ONLINE:
                this.isOnlineUnitFormOpen.set(true);
                break;
            case LectureUnitType.ATTACHMENT_VIDEO:
                this.isAttachmentVideoUnitFormOpen.set(true);
                break;
        }
    }

    isAnyUnitFormOpen = computed(() => {
        return this.isTextUnitFormOpen() || this.isOnlineUnitFormOpen() || this.isAttachmentVideoUnitFormOpen() || this.isExerciseUnitFormOpen();
    });

    onCloseLectureUnitForms() {
        this.isTextUnitFormOpen.set(false);
        this.isOnlineUnitFormOpen.set(false);
        this.isAttachmentVideoUnitFormOpen.set(false);
        this.isExerciseUnitFormOpen.set(false);
        this.isEditingLectureUnit = false;
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
                this.unitManagementComponent()?.loadData();
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
                this.unitManagementComponent()?.loadData();
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    createEditAttachmentVideoUnit(attachmentVideoUnitFormData: AttachmentVideoUnitFormData): void {
        const { description, name, releaseDate, videoSource, updateNotificationText, competencyLinks } = attachmentVideoUnitFormData.formProperties;

        const { file, fileName } = attachmentVideoUnitFormData.fileProperties;

        if (!name || (!fileName && !videoSource)) {
            return;
        }

        this.currentlyProcessedAttachmentVideoUnit = this.isEditingLectureUnit ? this.currentlyProcessedAttachmentVideoUnit : new AttachmentVideoUnit();
        const attachmentToCreateOrEdit =
            this.isEditingLectureUnit && this.currentlyProcessedAttachmentVideoUnit.attachment ? this.currentlyProcessedAttachmentVideoUnit.attachment : new Attachment();

        if (this.isEditingLectureUnit) {
            // breaking the connection to prevent errors in deserialization. will be reconnected on the server side
            this.currentlyProcessedAttachmentVideoUnit.attachment = undefined;
            if (attachmentToCreateOrEdit != null) {
                attachmentToCreateOrEdit.attachmentVideoUnit = undefined;
            }
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
            this.currentlyProcessedAttachmentVideoUnit.releaseDate = releaseDate;
            attachmentToCreateOrEdit.releaseDate = releaseDate;
        }
        attachmentToCreateOrEdit.attachmentType = AttachmentType.FILE;
        attachmentToCreateOrEdit.version = 1;
        attachmentToCreateOrEdit.uploadDate = dayjs();

        if (videoSource) {
            this.currentlyProcessedAttachmentVideoUnit.videoSource = videoSource;
        }

        if (description) {
            this.currentlyProcessedAttachmentVideoUnit.description = description;
        }
        this.currentlyProcessedAttachmentVideoUnit.competencyLinks = competencyLinks;

        const formData = new FormData();
        if (!!file && !!fileName) {
            formData.append('file', file, fileName);
            formData.append('attachment', objectToJsonBlob(attachmentToCreateOrEdit));
        }
        formData.append('attachmentVideoUnit', objectToJsonBlob(this.currentlyProcessedAttachmentVideoUnit));

        const save$ = this.isEditingLectureUnit
            ? this.attachmentVideoUnitService.update(this.lecture.id!, this.currentlyProcessedAttachmentVideoUnit.id!, formData, notificationText)
            : this.attachmentVideoUnitService.create(formData, this.lecture.id!);

        save$.subscribe({
            next: () => {
                this.onCloseLectureUnitForms();
                this.unitManagementComponent()?.loadData();
            },
            error: (res: HttpErrorResponse | Error) => {
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
    }

    /**
     * Called when all selected exercises were linked from the component
     */
    onExerciseUnitCreated() {
        this.onCloseLectureUnitForms();
        this.unitManagementComponent()?.loadData();
    }

    /**
     * Scrolls to the edit form container
     */
    private scrollToEditForm(): void {
        const container = this.editFormContainer();
        if (container?.nativeElement) {
            container.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    }

    startEditLectureUnit(lectureUnit: LectureUnit) {
        this.isEditingLectureUnit = true;

        lectureUnit.lecture = new Lecture();
        lectureUnit.lecture.id = this.lecture.id;
        lectureUnit.lecture.course = this.lecture.course;

        this.currentlyProcessedTextUnit = lectureUnit as TextUnit;
        this.currentlyProcessedOnlineUnit = lectureUnit as OnlineUnit;
        this.currentlyProcessedAttachmentVideoUnit = lectureUnit as AttachmentVideoUnit;

        this.isTextUnitFormOpen.set(lectureUnit.type === LectureUnitType.TEXT);
        this.isExerciseUnitFormOpen.set(lectureUnit.type === LectureUnitType.EXERCISE);
        this.isOnlineUnitFormOpen.set(lectureUnit.type === LectureUnitType.ONLINE);
        this.isAttachmentVideoUnitFormOpen.set(lectureUnit.type === LectureUnitType.ATTACHMENT_VIDEO);

        // Scroll to the edit form after a brief delay to allow the form to render
        setTimeout(() => this.scrollToEditForm(), 100);

        switch (lectureUnit.type) {
            case LectureUnitType.TEXT:
                this.textUnitFormData = {
                    name: this.currentlyProcessedTextUnit.name,
                    releaseDate: this.currentlyProcessedTextUnit.releaseDate,
                    content: this.currentlyProcessedTextUnit.content,
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
            case LectureUnitType.ATTACHMENT_VIDEO:
                this.attachmentVideoUnitFormData = {
                    formProperties: {
                        name: this.currentlyProcessedAttachmentVideoUnit.name,
                        description: this.currentlyProcessedAttachmentVideoUnit.description,
                        releaseDate: this.currentlyProcessedAttachmentVideoUnit.releaseDate,
                        version: this.currentlyProcessedAttachmentVideoUnit.attachment?.version,
                        videoSource: this.currentlyProcessedAttachmentVideoUnit.videoSource,
                    },
                    fileProperties: {
                        fileName: this.currentlyProcessedAttachmentVideoUnit.attachment?.link,
                    },
                };
                break;
        }
    }

    /**
     * Handles PDF files dropped on the drop zone
     * Creates attachment units for each file with name from filename and release date 15 min in future
     * Opens the edit form for the last created unit
     */
    onPdfFilesDropped(files: File[]): void {
        if (files.length === 0 || !this.lecture.id) {
            return;
        }

        this.isUploadingPdfs.set(true);
        let lastCreatedUnit: AttachmentVideoUnit | undefined;

        from(files)
            .pipe(
                concatMap((file) => this.createAttachmentUnitFromFile(file)),
                filter((response) => response.body != null),
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
                    this.unitManagementComponent()?.loadData();
                    // Open edit form for the last created unit
                    if (lastCreatedUnit) {
                        this.startEditLectureUnit(lastCreatedUnit);
                    }
                },
            });
    }

    /**
     * Creates a single attachment unit from a file
     */
    private createAttachmentUnitFromFile(file: File) {
        return this.attachmentVideoUnitService.createAttachmentVideoUnitFromFile(this.lecture.id!, file);
    }
}
