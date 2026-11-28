import { Component, inject, signal } from '@angular/core';
import { Course } from 'app/course/shared/entities/course.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiMessageComponent } from '@tumaet/ui-angular/message';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AbstractDialogComponent } from 'app/communication/course-conversations-components/abstract-dialog.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CourseUsersSelectorComponent } from 'app/communication/course-users-selector/course-users-selector.component';

@Component({
    selector: 'jhi-group-chat-create-dialog',
    templateUrl: './group-chat-create-dialog.component.html',
    imports: [
        TranslateDirective,
        FormsModule,
        ReactiveFormsModule,
        CourseUsersSelectorComponent,
        ArtemisTranslatePipe,
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiMessageComponent,
    ],
})
export class GroupChatCreateDialogComponent extends AbstractDialogComponent {
    private fb = inject(FormBuilder);

    protected readonly faXmark = faXmark;

    course = signal<Course | undefined>(undefined);
    form!: FormGroup; // set in initializeForm() from initialize()

    override initialize() {
        super.initialize(['course']);
        if (this.isInitialized) {
            this.initializeForm();
        }
    }

    private initializeForm() {
        if (this.form) {
            return;
        }
        const validators = [Validators.required, Validators.maxLength(9)];

        this.form = this.fb.group({
            selectedUsers: [[], validators],
        });
    }

    get selectedUsersControl() {
        return this.form.get('selectedUsers');
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }

    clear() {
        this.dismiss();
    }

    onSubmit() {
        this.close(this.selectedUsersControl?.value ?? []);
    }
}
