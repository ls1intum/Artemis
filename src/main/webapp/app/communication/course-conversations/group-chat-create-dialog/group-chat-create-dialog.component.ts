import { Component, Input, inject } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AbstractDialogComponent } from 'app/communication/course-conversations/abstract-dialog.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CourseUsersSelectorComponent } from 'app/communication/course-users-selector/course-users-selector.component';

@Component({
    selector: 'jhi-group-chat-create-dialog',
    templateUrl: './group-chat-create-dialog.component.html',
    imports: [TranslateDirective, FormsModule, ReactiveFormsModule, CourseUsersSelectorComponent, ArtemisTranslatePipe],
})
export class GroupChatCreateDialogComponent extends AbstractDialogComponent {
    private fb = inject(FormBuilder);

    @Input() course: Course;
    form: FormGroup;

    initialize() {
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
