import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { AlertService } from 'app/core/util/alert.service';
import { LectureService } from './lecture.service';
import { CourseManagementService } from '../course/manage/course-management.service';
import { Lecture } from 'app/entities/lecture.model';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { faCheck, faHandshakeAngle, faArrowRight, faPencilAlt } from '@fortawesome/free-solid-svg-icons';
import { onError } from 'app/shared/util/global.utils';
import { LearningGoalFormData } from 'app/course/learning-goals/learning-goal-form/learning-goal-form.component';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { TextUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/text-unit-form/text-unit-form.component';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { TextUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/textUnit.service';
import { LectureUnitManagementComponent } from 'app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.component';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { VideoUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/videoUnit.service';
import { VideoUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/video-unit-form/video-unit-form.component';
import { OnlineUnit } from 'app/entities/lecture-unit/onlineUnit.model';
import { OnlineUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/onlineUnit.service';
import { OnlineUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/online-unit-form/online-unit-form.component';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { AttachmentUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/attachment-unit-form/attachment-unit-form.component';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { objectToJsonBlob } from 'app/utils/blob-util';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-lecture-update-wizard',
    templateUrl: './lecture-update-wizard.component.html',
    styleUrls: ['./lecture-update-wizard.component.scss'],
})
export class LectureUpdateWizardComponent implements OnInit {
    @Input() toggleModeFunction: () => void;
    @Input() saveLectureFunction: () => void;
    @Input() lecture: Lecture;
    @Input() isSaving: boolean;
    @Input() startDate: string;
    @Input() endDate: string;

    @ViewChild(LectureUnitManagementComponent, { static: false }) unitManagementComponent: LectureUnitManagementComponent;

    currentStep: number;
    isAddingLearningGoal: boolean;
    isLoadingLearningGoalForm: boolean;
    isLoadingLearningGoals: boolean;
    isEditingLearningGoal: boolean;
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

    currentlyProcessedLearningGoal: LearningGoal;
    learningGoals: LearningGoal[] = [];
    learningGoalFormData: LearningGoalFormData;

    domainCommandsDescription = [new KatexCommand()];
    EditorMode = EditorMode;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faCheck = faCheck;
    faHandShakeAngle = faHandshakeAngle;
    faArrowRight = faArrowRight;
    faPencilAlt = faPencilAlt;

    constructor(
        protected alertService: AlertService,
        protected lectureService: LectureService,
        protected learningGoalService: LearningGoalService,
        protected courseService: CourseManagementService,
        protected textUnitService: TextUnitService,
        protected videoUnitService: VideoUnitService,
        protected onlineUnitService: OnlineUnitService,
        protected attachmentUnitService: AttachmentUnitService,
        protected activatedRoute: ActivatedRoute,
        private navigationUtilService: ArtemisNavigationUtilService,
        private router: Router,
    ) {}

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit() {
        this.isSaving = false;
        this.currentStep = this.lecture.startDate !== undefined || this.lecture.endDate !== undefined ? 2 : 1;
    }

    /**
     * Progress to the next step of the wizard mode
     */
    next() {
        if (this.currentStep === 2) {
            this.saveLectureFunction();
            return;
        }

        this.currentStep++;

        if (this.currentStep === 5) {
            this.loadLearningGoals();
        }

        if (this.currentStep > 5) {
            this.toggleWizardMode();
        }
    }

    onLectureCreationSucceeded() {
        this.currentStep++;
    }

    /**
     * Checks if the given step has already been completed
     */
    isCompleted(step: number) {
        return this.currentStep > step;
    }

    /**
     * Checks if the given step is the current one
     */
    isCurrent(step: number) {
        return this.currentStep === step;
    }

    getNextIcon() {
        return this.currentStep < 5 ? faArrowRight : faCheck;
    }

    getNextText() {
        return this.currentStep < 5 ? 'artemisApp.lecture.home.nextStepLabel' : 'entity.action.finish';
    }

    toggleWizardMode() {
        if (this.currentStep <= 2) {
            this.toggleModeFunction();
        } else {
            this.router.navigate(['course-management', this.lecture.course!.id, 'lectures', this.lecture.id]);
        }
    }

    showCreateLearningGoal() {
        this.isLoadingLearningGoalForm = true;
        this.isAddingLearningGoal = !this.isAddingLearningGoal;
        this.learningGoalFormData = {
            id: undefined,
            title: undefined,
            description: undefined,
            taxonomy: undefined,
            connectedLectureUnits: undefined,
        };

        this.subscribeToLoadUnitResponse(this.lectureService.findWithDetails(this.lecture.id!));
    }

    protected subscribeToLoadUnitResponse(result: Observable<HttpResponse<Lecture>>) {
        result.subscribe({
            next: (response: HttpResponse<Lecture>) => this.onLoadUnitSuccess(response.body!),
            error: (error: HttpErrorResponse) => this.onLoadError(error),
        });
    }

    protected subscribeToLoadLearningGoalsResponse(result: Observable<HttpResponse<LearningGoal[]>>) {
        result.subscribe({
            next: (response: HttpResponse<LearningGoal[]>) => this.onLoadLearningGoalsSuccess(response.body!),
            error: (error: HttpErrorResponse) => this.onLoadError(error),
        });
    }

    /**
     * Action on successful lecture unit fetch
     */
    protected onLoadUnitSuccess(lecture: Lecture) {
        this.lecture = lecture;

        this.isLoadingLearningGoalForm = false;
    }

    /**
     * Action on successful learning goals fetch
     */
    protected onLoadLearningGoalsSuccess(learningGoals: LearningGoal[]) {
        this.isLoadingLearningGoals = false;

        this.learningGoals = learningGoals;
    }

    /**
     * Action on unsuccessful fetch
     * @param error the error handed to the alert service
     */
    protected onLoadError(error: HttpErrorResponse) {
        this.isSaving = false;
        this.isLoadingLearningGoalForm = false;
        this.isLoadingLearningGoals = false;

        onError(this.alertService, error);
    }

    onLearningGoalFormSubmitted(formData: LearningGoalFormData) {
        if (this.isEditingLearningGoal) {
            this.editLearningGoal(formData);
        } else {
            this.createLearningGoal(formData);
        }
    }

    createLearningGoal(formData: LearningGoalFormData) {
        if (!formData?.title) {
            return;
        }

        const { title, description, taxonomy, connectedLectureUnits } = formData;
        this.currentlyProcessedLearningGoal = new LearningGoal();

        this.currentlyProcessedLearningGoal.title = title;
        this.currentlyProcessedLearningGoal.description = description;
        this.currentlyProcessedLearningGoal.taxonomy = taxonomy;
        this.currentlyProcessedLearningGoal.lectureUnits = connectedLectureUnits;

        this.isLoadingLearningGoalForm = true;

        this.learningGoalService
            .create(this.currentlyProcessedLearningGoal!, this.lecture.course!.id!)
            .pipe(
                finalize(() => {
                    this.isLoadingLearningGoalForm = false;
                }),
            )
            .subscribe({
                next: (response: HttpResponse<LearningGoal>) => {
                    this.isAddingLearningGoal = false;
                    this.learningGoals = this.learningGoals.concat(response.body!);

                    this.alertService.success(`Learning goal ${this.currentlyProcessedLearningGoal.title} was successfully created.`);
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    editLearningGoal(formData: LearningGoalFormData) {
        const { title, description, taxonomy, connectedLectureUnits } = formData;

        this.currentlyProcessedLearningGoal.title = title;
        this.currentlyProcessedLearningGoal.description = description;
        this.currentlyProcessedLearningGoal.taxonomy = taxonomy;
        this.currentlyProcessedLearningGoal.lectureUnits = connectedLectureUnits;

        this.isLoadingLearningGoalForm = true;

        this.learningGoalService
            .update(this.currentlyProcessedLearningGoal, this.lecture.course!.id!)
            .pipe(
                finalize(() => {
                    this.isLoadingLearningGoalForm = false;
                }),
            )
            .subscribe({
                next: (response: HttpResponse<LearningGoal>) => {
                    this.isEditingLearningGoal = false;
                    const index = this.learningGoals.findIndex((learningGoal) => learningGoal.id === this.currentlyProcessedLearningGoal.id);
                    if (index === -1) {
                        this.learningGoals = this.learningGoals.concat(response.body!);
                    } else {
                        this.learningGoals[index] = response.body!;
                    }

                    this.currentlyProcessedLearningGoal = new LearningGoal();
                    this.alertService.success(`Learning goal ${this.currentlyProcessedLearningGoal.title} was successfully edited.`);
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    trackLearningGoalId(index: number, item: LearningGoal) {
        return item.id;
    }

    loadLearningGoals() {
        this.isLoadingLearningGoals = true;

        this.subscribeToLoadLearningGoalsResponse(this.learningGoalService.getAllForCourse(this.lecture.course!.id!));
    }

    getConnectedUnitsForLearningGoal(learningGoal: LearningGoal) {
        const units = learningGoal.lectureUnits?.filter((unit) => unit.lecture?.id === this.lecture.id);

        if (units === undefined || units.length === 0) {
            return 'No connected units';
        }

        return units.map((unit) => unit.name).join(', ');
    }

    startEditLearningGoal(learningGoal: LearningGoal) {
        const connectedUnits: LectureUnit[] = [];
        learningGoal.lectureUnits!.forEach((unit) => connectedUnits.push(Object.assign({}, unit)));

        this.learningGoalFormData = {
            id: learningGoal.id,
            title: learningGoal.title,
            description: learningGoal.description,
            taxonomy: learningGoal.taxonomy,
            connectedLectureUnits: connectedUnits,
        };

        this.isLoadingLearningGoalForm = true;
        this.isEditingLearningGoal = true;
        this.currentlyProcessedLearningGoal = learningGoal;

        this.subscribeToLoadUnitResponse(this.lectureService.findWithDetails(this.lecture.id!));
    }

    deleteLearningGoal(learningGoal: LearningGoal) {
        this.learningGoalService.delete(learningGoal.id!, this.lecture.course!.id!).subscribe({
            next: () => {
                this.learningGoals = this.learningGoals.filter((existingLearningGoal) => existingLearningGoal.id !== learningGoal.id);
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    onLearningGoalFormCanceled() {
        this.isAddingLearningGoal = false;
        this.isEditingLearningGoal = false;
        this.isLoadingLearningGoalForm = false;

        this.currentlyProcessedLearningGoal = new LearningGoal();
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

    createTextUnit(formData: TextUnitFormData) {
        if (!formData?.name) {
            return;
        }

        const { name, releaseDate, content } = formData;

        this.currentlyProcessedTextUnit = new TextUnit();
        this.currentlyProcessedTextUnit.name = name;
        this.currentlyProcessedTextUnit.releaseDate = releaseDate;
        this.currentlyProcessedTextUnit.content = content;

        this.textUnitService.create(this.currentlyProcessedTextUnit!, this.lecture.id!).subscribe({
            next: () => {
                this.onCloseLectureUnitForms();
                this.unitManagementComponent.loadData();
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    createVideoUnit(formData: VideoUnitFormData) {
        if (!formData?.name || !formData?.source) {
            return;
        }

        const { name, description, releaseDate, source } = formData;

        this.currentlyProcessedVideoUnit = new VideoUnit();
        this.currentlyProcessedVideoUnit.name = name || undefined;
        this.currentlyProcessedVideoUnit.releaseDate = releaseDate || undefined;
        this.currentlyProcessedVideoUnit.description = description || undefined;
        this.currentlyProcessedVideoUnit.source = source || undefined;

        this.videoUnitService.create(this.currentlyProcessedVideoUnit!, this.lecture.id!).subscribe({
            next: () => {
                this.onCloseLectureUnitForms();
                this.unitManagementComponent.loadData();
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    createOnlineUnit(formData: OnlineUnitFormData) {
        if (!formData?.name || !formData?.source) {
            return;
        }

        const { name, description, releaseDate, source } = formData;

        this.currentlyProcessedOnlineUnit = new OnlineUnit();
        this.currentlyProcessedOnlineUnit.name = name || undefined;
        this.currentlyProcessedOnlineUnit.releaseDate = releaseDate || undefined;
        this.currentlyProcessedOnlineUnit.description = description || undefined;
        this.currentlyProcessedOnlineUnit.source = source || undefined;

        this.onlineUnitService.create(this.currentlyProcessedOnlineUnit!, this.lecture.id!).subscribe({
            next: () => {
                this.onCloseLectureUnitForms();
                this.unitManagementComponent.loadData();
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    createAttachmentUnit(attachmentUnitFormData: AttachmentUnitFormData): void {
        if (!attachmentUnitFormData?.formProperties?.name || !attachmentUnitFormData?.fileProperties?.file || !attachmentUnitFormData?.fileProperties?.fileName) {
            return;
        }
        const { description, name, releaseDate } = attachmentUnitFormData.formProperties;
        const { file, fileName } = attachmentUnitFormData.fileProperties;

        this.currentlyProcessedAttachmentUnit = new AttachmentUnit();
        const attachmentToCreate = new Attachment();

        if (name) {
            attachmentToCreate.name = name;
        }
        if (releaseDate) {
            attachmentToCreate.releaseDate = releaseDate;
        }
        attachmentToCreate.attachmentType = AttachmentType.FILE;
        attachmentToCreate.version = 1;
        attachmentToCreate.uploadDate = dayjs();

        if (description) {
            this.currentlyProcessedAttachmentUnit.description = description;
        }

        const formData = new FormData();
        formData.append('file', file, fileName);
        formData.append('attachment', objectToJsonBlob(attachmentToCreate));
        formData.append('attachmentUnit', objectToJsonBlob(this.currentlyProcessedAttachmentUnit));

        this.attachmentUnitService.create(formData, this.lecture.id!).subscribe({
            next: () => {
                this.onCloseLectureUnitForms();
                this.unitManagementComponent.loadData();
            },
            error: (res: HttpErrorResponse) => {
                if (res.error.params === 'file' && res?.error?.title) {
                    this.alertService.error(res.error.title);
                } else {
                    onError(this.alertService, res);
                }
            },
        });
    }

    startEditLectureUnit(lectureUnit: LectureUnit) {
        this.isEditingLectureUnit = true;

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

    // onLearningGoalFormSubmitted(formData: LearningGoalFormData) {
    //     if (this.isEditingLearningGoal) {
    //         this.editLearningGoal(formData);
    //     } else {
    //         this.createLearningGoal(formData);
    //     }
    // }
    // editLearningGoal(formData: LearningGoalFormData) {
    //     const { title, description, taxonomy, connectedLectureUnits } = formData;
    //
    //     this.currentlyProcessedLearningGoal.title = title;
    //     this.currentlyProcessedLearningGoal.description = description;
    //     this.currentlyProcessedLearningGoal.taxonomy = taxonomy;
    //     this.currentlyProcessedLearningGoal.lectureUnits = connectedLectureUnits;
    //
    //     this.isLoadingLearningGoalForm = true;
    //
    //     this.learningGoalService
    //         .update(this.currentlyProcessedLearningGoal, this.lecture.course!.id!)
    //         .pipe(
    //             finalize(() => {
    //                 this.isLoadingLearningGoalForm = false;
    //             }),
    //         )
    //         .subscribe({
    //             next: (response: HttpResponse<LearningGoal>) => {
    //                 this.isEditingLearningGoal = false;
    //                 const index = this.learningGoals.findIndex((learningGoal) => learningGoal.id === this.currentlyProcessedLearningGoal.id);
    //                 if (index === -1) {
    //                     this.learningGoals = this.learningGoals.concat(response.body!);
    //                 } else {
    //                     this.learningGoals[index] = response.body!;
    //                 }
    //
    //                 this.currentlyProcessedLearningGoal = new LearningGoal();
    //                 this.alertService.success(`Learning goal ${this.currentlyProcessedLearningGoal.title} was successfully edited.`);
    //             },
    //             error: (res: HttpErrorResponse) => onError(this.alertService, res),
    //         });
    // }
}
