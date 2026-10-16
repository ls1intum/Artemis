import { Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { Subscription } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { AlertService } from 'app/foundation/service/alert.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ExamUsersNotFoundDTO } from 'app/exam/shared/entities/exam-users-not-found-dto.model';
import { faBan, faCheck, faCircleNotch, faSpinner, faUpload } from '@fortawesome/free-solid-svg-icons';
import { onError } from 'app/foundation/util/global.utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgOptimizedImage } from '@angular/common';
import { TumAetUiButtonDirective, TumAetUiDialogComponent } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-student-upload-images-dialog',
    templateUrl: './students-upload-images-dialog.component.html',
    imports: [NgOptimizedImage, FormsModule, TranslateDirective, HelpIconComponent, FaIconComponent, TumAetUiButtonDirective, TumAetUiDialogComponent, ArtemisTranslatePipe],
})
export class StudentsUploadImagesDialogComponent {
    private alertService = inject(AlertService);
    private examManagementService = inject(ExamManagementService);

    readonly visible = model(false);
    readonly courseId = input.required<number>();
    readonly exam = input.required<Exam>();
    /** Emitted when the user finishes after the images were saved, so the parent can reload the students. */
    readonly finished = output<void>();

    notFoundUsers = signal<ExamUsersNotFoundDTO | undefined>(undefined);
    file = signal<File | undefined>(undefined);

    private uploadSubscription: Subscription | undefined;

    isParsing = signal(false);
    hasParsed = signal(false);

    readonly usersNotFound = computed(() => this.notFoundUsers()?.numberOfUsersNotFound ?? 0);
    readonly imagesSaved = computed(() => this.notFoundUsers()?.numberOfImagesSaved ?? 0);

    // Icons
    faBan = faBan;
    faSpinner = faSpinner;
    faCheck = faCheck;
    faCircleNotch = faCircleNotch;
    faUpload = faUpload;

    constructor() {
        // Every opening starts with an empty form, since the dialog stays mounted in the page. The form is cleared on closing, so nothing resets input made right after opening.
        effect(() => {
            if (!this.visible()) {
                untracked(() => {
                    // A response that arrives after closing must not restore the results into the next opening.
                    this.uploadSubscription?.unsubscribe();
                    this.resetDialog();
                    this.file.set(undefined);
                });
            }
        });
    }

    open() {
        this.visible.set(true);
    }

    clear() {
        this.visible.set(false);
    }

    onFinish() {
        this.visible.set(false);
        this.finished.emit();
    }

    private resetDialog() {
        this.isParsing.set(false);
        this.notFoundUsers.set(undefined);
        this.hasParsed.set(false);
    }

    onPDFFileSelect(event: Event) {
        const files = (event.target as HTMLInputElement).files;
        if (files && files.length > 0) {
            this.resetDialog();
            this.file.set(files[0]);
        }
    }

    /**
     * Parse pdf file and save images of registered students
     */
    parsePDFFile() {
        this.isParsing.set(true);
        const exam = this.exam();
        if (exam?.id) {
            const formData: FormData = new FormData();
            formData.append('file', this.file()!);

            this.uploadSubscription = this.examManagementService.saveImages(this.courseId(), exam.id, formData).subscribe({
                next: (res: HttpResponse<ExamUsersNotFoundDTO>) => {
                    if (res) {
                        this.notFoundUsers.set(res.body ?? undefined);
                        this.isParsing.set(false);
                        this.hasParsed.set(true);
                    }
                },
                error: (res: HttpErrorResponse) => {
                    if (res.error.params === 'file' && res?.error?.title) {
                        this.alertService.error(res.error.title);
                    } else {
                        onError(this.alertService, res);
                    }
                    this.isParsing.set(false);
                    this.hasParsed.set(false);
                },
            });
        }
    }
}
