import { ActivatedRoute } from '@angular/router';
import { Component, OnInit, ViewChild } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/alert/alert.service';
import { Observable } from 'rxjs';
import { ImageCroppedEvent } from 'ngx-image-cropper';
import { base64StringToBlob } from 'blob-util';

import { regexValidator } from 'app/shared/form/shortname-validator.directive';

import { Course } from 'app/entities/course.model';
import { CourseManagementService } from './course-management.service';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { CachingStrategy } from 'app/shared/image/secured-image.component';

@Component({
    selector: 'jhi-course-update',
    templateUrl: './course-update.component.html',
    styleUrls: ['./course-update.component.scss'],
})
export class CourseUpdateComponent implements OnInit {
    CachingStrategy = CachingStrategy;

    @ViewChild(ColorSelectorComponent, { static: false }) colorSelector: ColorSelectorComponent;
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    courseForm: FormGroup;
    course: Course;
    isSaving: boolean;
    courseImageFile: Blob | File | null;
    courseImageFileName: string;
    isUploadingCourseImage: boolean;
    imageChangedEvent: any = '';
    croppedImage: any = '';
    showCropper = false;
    presentationScoreEnabled = false;

    shortNamePattern = /^[a-zA-Z][a-zA-Z0-9]*$/; // must start with a letter and cannot contain special characters
    presentationScorePattern = /^[0-9]{0,4}$/; // makes sure that the presentation score is a positive natural integer greater than 0 and not too large

    constructor(
        private courseService: CourseManagementService,
        private activatedRoute: ActivatedRoute,
        private fileUploaderService: FileUploaderService,
        private jhiAlertService: AlertService,
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ course }) => {
            this.course = course;
        });
        this.courseForm = new FormGroup({
            id: new FormControl(this.course.id),
            title: new FormControl(this.course.title, [Validators.required]),
            shortName: new FormControl(this.course.shortName, {
                validators: [Validators.required, Validators.minLength(3), regexValidator(this.shortNamePattern)],
                updateOn: 'blur',
            }),
            maxComplaints: new FormControl(this.course.maxComplaints, {
                //TODO: allow 0 (no complaints) ?
                validators: [Validators.required, Validators.min(0)],
            }),
            studentGroupName: new FormControl(this.course.studentGroupName, [Validators.required]),
            teachingAssistantGroupName: new FormControl(this.course.teachingAssistantGroupName),
            instructorGroupName: new FormControl(this.course.instructorGroupName, [Validators.required]),
            description: new FormControl(this.course.description),
            startDate: new FormControl(this.course.startDate),
            endDate: new FormControl(this.course.endDate),
            onlineCourse: new FormControl(this.course.onlineCourse),
            registrationEnabled: new FormControl(this.course.registrationEnabled),
            presentationScore: new FormControl({ value: this.course.presentationScore, disabled: this.course.presentationScore === 0 }, [
                Validators.min(1),
                regexValidator(this.presentationScorePattern),
            ]),
            color: new FormControl(this.course.color),
            courseIcon: new FormControl(this.course.courseIcon),
        });
        this.courseImageFileName = this.course.courseIcon;
        this.croppedImage = this.course.courseIcon ? this.course.courseIcon : '';
        this.presentationScoreEnabled = this.course.presentationScore !== 0;
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.course.id !== undefined) {
            this.subscribeToSaveResponse(this.courseService.update(this.courseForm.getRawValue()));
        } else {
            this.subscribeToSaveResponse(this.courseService.create(this.courseForm.getRawValue()));
        }
    }

    openColorSelector(event: MouseEvent) {
        this.colorSelector.openColorSelector(event);
    }

    onSelectedColor(selectedColor: string) {
        this.courseForm.patchValue({ color: selectedColor });
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<Course>>) {
        result.subscribe(
            (res: HttpResponse<Course>) => this.onSaveSuccess(),
            (res: HttpErrorResponse) => this.onSaveError(res),
        );
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    /**
     * @function set course icon
     * @param $event {object} Event object which contains the uploaded file
     */
    setCourseImage($event: any): void {
        this.imageChangedEvent = $event;
        if ($event.target.files.length) {
            const fileList: FileList = $event.target.files;
            this.courseImageFile = fileList[0];
            this.courseImageFileName = this.courseImageFile['name'];
        }
    }

    /**
     * @param $event
     */
    imageCropped($event: ImageCroppedEvent) {
        this.croppedImage = $event.base64;
    }

    imageLoaded() {
        this.showCropper = true;
    }

    cropperReady() {
        console.log('Cropper ready');
    }

    loadImageFailed() {
        console.log('Load failed');
    }

    /**
     * @function uploadBackground
     * @desc Upload the selected file (from "Upload Background") and use it for the question's backgroundFilePath
     */
    uploadCourseImage(): void {
        const contentType = 'image/*';
        const b64Data = this.croppedImage.replace('data:image/png;base64,', '');
        const file = base64StringToBlob(b64Data, contentType);
        file['name'] = this.courseImageFileName;

        this.isUploadingCourseImage = true;
        this.fileUploaderService.uploadFile(file, file['name']).then(
            result => {
                this.courseForm.patchValue({ courseIcon: result.path });
                this.isUploadingCourseImage = false;
                this.courseImageFile = null;
                this.courseImageFileName = result.path;
            },
            error => {
                console.error('Error during file upload in uploadBackground()', error.message);
                this.isUploadingCourseImage = false;
                this.courseImageFile = null;
                this.courseImageFileName = this.course.courseIcon;
            },
        );
        this.showCropper = false;
    }

    private onSaveError(error: HttpErrorResponse) {
        const errorMessage = error.headers.get('X-artemisApp-alert')!;
        // TODO: this is a workaround to avoid translation not found issues. Provide proper translations
        const jhiAlert = this.jhiAlertService.error(errorMessage);
        jhiAlert.msg = errorMessage;
        this.isSaving = false;
    }

    get shortName() {
        return this.courseForm.get('shortName')!;
    }

    /**
     * Enable and disable presentation score input field based on presentationScoreEnabled checkbox
     */
    togglePresentationScoreInput() {
        const presentationScoreControl = this.courseForm.controls['presentationScore'];
        if (presentationScoreControl.disabled) {
            presentationScoreControl.enable();
            this.presentationScoreEnabled = true;
        } else {
            presentationScoreControl.reset({ value: 0, disabled: true });
            this.presentationScoreEnabled = false;
        }
    }
}
