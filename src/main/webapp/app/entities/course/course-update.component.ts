import { ActivatedRoute } from '@angular/router';
import { Component, OnInit, ViewChild } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { Observable } from 'rxjs';

import { regexValidator } from 'app/shared/form/shortname-validator.directive';

import { Course } from './course.model';
import { CourseService } from './course.service';
import { ColorSelectorComponent } from 'app/components/color-selector/color-selector.component';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';

@Component({
    selector: 'jhi-course-update',
    templateUrl: './course-update.component.html',
    styles: ['.color-preview { cursor: pointer; }']
})
export class CourseUpdateComponent implements OnInit {
    @ViewChild(ColorSelectorComponent) colorSelector: ColorSelectorComponent;
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    courseForm: FormGroup;
    course: Course;
    isSaving: boolean;
    courseImageFile: Blob | File;
    courseImageFileName: string;
    isUploadingCourseImage: boolean;

    shortNamePattern = /^[a-zA-Z][a-zA-Z0-9]*$/; // must start with a letter and cannot contain special characters

    constructor(
        private courseService: CourseService,
        private activatedRoute: ActivatedRoute,
        private fileUploaderService: FileUploaderService,
        private jhiAlertService: JhiAlertService) {}

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
                updateOn: 'blur'
            }),
            studentGroupName: new FormControl(this.course.studentGroupName, [Validators.required]),
            teachingAssistantGroupName: new FormControl(this.course.teachingAssistantGroupName),
            instructorGroupName: new FormControl(this.course.instructorGroupName, [Validators.required]),
            description: new FormControl(this.course.description),
            startDate: new FormControl(this.course.startDate),
            endDate: new FormControl(this.course.endDate),
            onlineCourse: new FormControl(this.course.onlineCourse),
            registrationEnabled: new FormControl(this.course.registrationEnabled),
            color: new FormControl(this.course.color),
            courseIcon: new FormControl(this.course.courseIcon)
        });
        this.courseImageFileName = this.course.courseIcon;
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.course.id !== undefined) {
            this.subscribeToSaveResponse(this.courseService.update(this.courseForm.value));
        } else {
            this.subscribeToSaveResponse(this.courseService.create(this.courseForm.value));
        }
    }

    openColorSelector(event: MouseEvent) {
        this.colorSelector.openColorSelector(event);
    }

    onSelectedColor(selectedColor: string) {
        this.courseForm.patchValue({'color': selectedColor});
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<Course>>) {
        result.subscribe((res: HttpResponse<Course>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError(res));
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    /**
     * @function setBackgroundFile
     * @param $event {object} Event object which contains the uploaded file
     */
    setCourseImage($event: any): void {
        if ($event.target.files.length) {
            const fileList: FileList = $event.target.files;
            this.courseImageFile = fileList[0];
            this.courseImageFileName = this.courseImageFile['name'];
        }
    }

    /**
     * @function uploadBackground
     * @desc Upload the selected file (from "Upload Background") and use it for the question's backgroundFilePath
     */
    uploadCourseImage(): void {
        const file = this.courseImageFile;

        this.isUploadingCourseImage = true;
        this.fileUploaderService.uploadFile(file, file['name']).then(
            result => {
                this.courseForm.patchValue({'courseIcon': result.path});
                this.isUploadingCourseImage = false;
                this.courseImageFile = null;
                this.courseImageFileName = result.path;
            },
            error => {
                console.error('Error during file upload in uploadBackground()', error.message);
                this.isUploadingCourseImage = false;
                this.courseImageFile = null;
                this.courseImageFileName = this.course.courseIcon;
            }
        );
    }

    private onSaveError(error: HttpErrorResponse) {
        const errorMessage = error.headers.get('X-arTeMiSApp-alert');
        // TODO: this is a workaround to avoid translation not found issues. Provide proper translations
        const jhiAlert = this.jhiAlertService.error(errorMessage);
        jhiAlert.msg = errorMessage;
        this.isSaving = false;
    }

    get shortName() {
        return this.courseForm.get('shortName');
    }
}
