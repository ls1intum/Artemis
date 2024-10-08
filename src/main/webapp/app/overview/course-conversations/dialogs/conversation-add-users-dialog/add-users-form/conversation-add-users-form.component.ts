import { Component, EventEmitter, Input, OnChanges, OnInit, Output, inject } from '@angular/core';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';

export interface AddUsersFormData {
    selectedUsers?: UserPublicInfoDTO[];
    addAllStudents: boolean;
    // all tutors also includes editors
    addAllTutors: boolean;
    addAllInstructors: boolean;
}

@Component({
    selector: 'jhi-conversation-add-users-form',
    templateUrl: './conversation-add-users-form.component.html',
})
export class ConversationAddUsersFormComponent implements OnInit, OnChanges {
    private fb = inject(FormBuilder);

    @Output() formSubmitted: EventEmitter<AddUsersFormData> = new EventEmitter<AddUsersFormData>();
    @Input() courseId: number;
    @Input() maxSelectable?: number = undefined;

    @Input()
    activeConversation: ConversationDTO;

    form: FormGroup;

    getAsChannel = getAsChannelDTO;

    mode: 'individual' | 'group' = 'individual';

    get selectedUsersControl() {
        return this.form.get('selectedUsers');
    }

    get isSubmitPossible() {
        return (
            (this.mode === 'individual' && !this.form.invalid) ||
            (this.mode === 'group' && (this.form.value?.addAllStudents || this.form.value?.addAllTutors || this.form.value?.addAllInstructors))
        );
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
            addAllStudents: [false],
            addAllTutors: [false],
            addAllInstructors: [false],
        });
    }

    submitForm() {
        this.formSubmitted.emit({ ...this.form.value });
    }
}
