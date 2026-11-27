import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { UserPublicInfoDTO } from 'app/account/user/user.model';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { getAsChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { faSpinner } from '@fortawesome/free-solid-svg-icons/faSpinner';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CourseUsersSelectorComponent } from 'app/communication/course-users-selector/course-users-selector.component';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiCheckboxComponent } from '@tumaet/ui-angular/checkbox';
import { TumAetUiMessageComponent } from '@tumaet/ui-angular/message';
import { TumAetUiSelectButtonComponent } from '@tumaet/ui-angular/select-button';

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
    imports: [
        TranslateDirective,
        FormsModule,
        ReactiveFormsModule,
        CourseUsersSelectorComponent,
        FaIconComponent,
        ArtemisTranslatePipe,
        TumAetUiButtonDirective,
        TumAetUiCheckboxComponent,
        TumAetUiMessageComponent,
        TumAetUiSelectButtonComponent,
    ],
})
export class ConversationAddUsersFormComponent implements OnInit {
    private fb = inject(FormBuilder);
    private translateService = inject(TranslateService);

    readonly formSubmitted = output<AddUsersFormData>();

    readonly courseId = input.required<number>();
    readonly maxSelectable = input<number | undefined>(undefined);
    readonly activeConversation = input.required<ConversationDTO>();

    isLoading = input<boolean>(false);

    form!: FormGroup; // set in initializeForm() from ngOnInit()

    // Icons
    protected readonly faSpinner = faSpinner;

    getAsChannel = getAsChannelDTO;

    mode: 'individual' | 'group' = 'individual';
    readonly modeOptions = signal<{ label: string; value: 'individual' | 'group' }[]>([]);

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
        this.modeOptions.set([
            { label: this.translateService.instant('artemisApp.dialogs.addUsers.addUsersForm.addIndividualUsers'), value: 'individual' },
            { label: this.translateService.instant('artemisApp.dialogs.addUsers.addUsersForm.addWholeGroups'), value: 'group' },
        ]);
        this.initializeForm();
    }

    private initializeForm() {
        if (this.form) {
            return;
        }
        const maxSel = this.maxSelectable();
        const validators = maxSel ? [Validators.required, Validators.maxLength(maxSel)] : [Validators.required];

        this.form = this.fb.group({
            selectedUsers: [[], validators],
            addAllStudents: [false],
            addAllTutors: [false],
            addAllInstructors: [false],
        });
    }

    submitForm() {
        this.formSubmitted.emit(deepClone(this.form.value));
    }
}
