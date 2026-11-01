import { TumAetUiTableDirective } from '@tumaet/ui-angular';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Component, computed, input } from '@angular/core';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { addPublicFilePrefix } from 'app/app.constants';
import { TutorialGroupStudent } from 'app/openapi/model/tutorial-group-student';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

export interface TutorialRegistrationsStudentsTableRemoveActionColumnInfo {
    headerStringKey: string;
    onRemove: (event: Event, student: TutorialGroupStudent) => void;
}

@Component({
    selector: 'jhi-tutorial-registrations-students-table',
    imports: [TumAetUiTableDirective, ProfilePictureComponent, TranslateDirective, TumAetUiButtonDirective, FaIconComponent, ArtemisTranslatePipe],
    templateUrl: './tutorial-registrations-students-table.component.html',
    styleUrl: './tutorial-registrations-students-table.component.scss',
})
export class TutorialRegistrationsStudentsTableComponent {
    protected readonly faXmark = faXmark;

    students = input.required<TutorialGroupStudent[]>();
    removeActionColumnInfo = input<TutorialRegistrationsStudentsTableRemoveActionColumnInfo>();
    studentsWithCompleteProfilePictureUrl = computed(() => this.computeStudentsWithCompleteProfilePictureUrl());

    private computeStudentsWithCompleteProfilePictureUrl(): TutorialGroupStudent[] {
        return this.students().map((student) => cloneWith(student, { profilePictureUrl: addPublicFilePrefix(student.profilePictureUrl) }));
    }
}
