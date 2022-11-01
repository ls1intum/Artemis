import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';

export interface AddUsersFormData {
    selectedUsers?: User[];
}

@Component({
    selector: 'jhi-add-users-form',
    templateUrl: './add-users-form.component.html',
    styleUrls: ['./add-users-form.component.scss'],
})
export class AddUsersFormComponent implements OnInit, OnChanges {
    @Output() formSubmitted: EventEmitter<AddUsersFormData> = new EventEmitter<AddUsersFormData>();
    @Input() courseId: number;
    @Input() maxSelectable = undefined;

    formData: AddUsersFormData = {
        selectedUsers: [],
    };

    form: FormGroup;

    constructor(private fb: FormBuilder) {}

    get selectedUsersControl() {
        return this.form.get('selectedUsers');
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }

    ngOnInit(): void {
        this.initializeForm();
    }

    ngOnChanges(): void {
        this.initializeForm();
    }

    private initializeForm() {
        if (this.form) {
            return;
        }
        const validators = this.maxSelectable ? [Validators.required, Validators.maxLength(this.maxSelectable)] : [Validators.required];

        this.form = this.fb.group({
            selectedUsers: [[], validators],
        });
    }

    submitForm() {
        this.formSubmitted.emit({ ...this.form.value });
    }
}
