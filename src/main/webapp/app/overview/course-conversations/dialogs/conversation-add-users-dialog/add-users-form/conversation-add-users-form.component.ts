import { Component, EventEmitter, Input, OnChanges, OnInit, Output, input } from '@angular/core';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';

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
    @Output() formSubmitted: EventEmitter<AddUsersFormData> = new EventEmitter<AddUsersFormData>();
    @Input() courseId: number;
    @Input() maxSelectable?: number = undefined;

    @Input()
    activeConversation: ConversationDTO;

    protected readonly isLoading = input<boolean>(false);

    form: FormGroup;

    // Icons
    protected readonly faSpinner = faSpinner;

    getAsChannel = getAsChannelDTO;

    mode: 'individual' | 'group' = 'individual';
    constructor(private fb: FormBuilder) {}

    get selectedUsersControl() {
        return this.form.get('selectedUsers');
    }

    get isSubmitPossible() {
        return (
            !this.isLoading() &&
            ((this.mode === 'individual' && !this.form.invalid) ||
                (this.mode === 'group' && (this.form.value?.addAllStudents || this.form.value?.addAllTutors || this.form.value?.addAllInstructors)))
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
