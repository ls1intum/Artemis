import { Component, Input } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';

@Component({
    selector: 'jhi-group-chat-create-dialog',
    templateUrl: './group-chat-create-dialog.component.html',
    styleUrls: ['./group-chat-create-dialog.component.scss'],
})
export class GroupChatCreateDialogComponent {
    @Input()
    course: Course;
    isInitialized = false;
    form: FormGroup;

    constructor(private activeModal: NgbActiveModal, private fb: FormBuilder) {}

    initialize() {
        if (!this.course) {
            console.error('Error: Dialog not fully configured');
        } else {
            this.isInitialized = true;
            this.initializeForm();
        }
    }

    private initializeForm() {
        if (this.form) {
            return;
        }
        const validators = [Validators.required, Validators.maxLength(10)];

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
        this.activeModal.dismiss();
    }

    onSubmit() {
        this.activeModal.close(this.selectedUsersControl?.value ?? []);
    }
}
