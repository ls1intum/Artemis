import { Component, EventEmitter, Input, OnChanges, OnInit, Output, inject, input } from '@angular/core';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { getAsChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CourseUsersSelectorComponent } from 'app/communication/course-users-selector/course-users-selector.component';

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
    imports: [TranslateDirective, FormsModule, ReactiveFormsModule, CourseUsersSelectorComponent, FaIconComponent, ArtemisTranslatePipe],
})
export class ConversationAddUsersFormComponent implements OnInit, OnChanges {
    private fb = inject(FormBuilder);

    @Output() formSubmitted: EventEmitter<AddUsersFormData> = new EventEmitter<AddUsersFormData>();

    @Input() courseId: number;
    @Input() maxSelectable?: number = undefined;
    @Input() activeConversation: ConversationDTO;

    isLoading = input<boolean>(false);

    form: FormGroup;

    // Icons
    protected readonly faSpinner = faSpinner;

    getAsChannel = getAsChannelDTO;

    mode: 'individual' | 'group' = 'individual';

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

    ngOnChanges() {
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
        this.formSubmitted.emit(Object.assign({}, this.form.value));
    }
}
