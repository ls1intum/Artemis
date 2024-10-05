import { Component, Input, inject } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AbstractDialogComponent } from 'app/overview/course-conversations/dialogs/abstract-dialog.component';

@Component({
    selector: 'jhi-group-chat-create-dialog',
    templateUrl: './group-chat-create-dialog.component.html',
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
