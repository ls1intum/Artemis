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
    imports: [ProfilePictureComponent, TranslateDirective],
    templateUrl: './tutorial-registrations-students-table.component.html',
    styleUrl: './tutorial-registrations-students-table.component.scss',
})
export class TutorialRegistrationsStudentsTableComponent {
    students = input.required<TutorialGroupStudent[]>();
    removeActionColumnInfo = input<TutorialRegistrationsStudentsTableRemoveActionColumnInfo>();
    studentsWithCompleteProfilePictureUrl = computed(() => this.computeStudentsWithCompleteProfilePictureUrl());

    private computeStudentsWithCompleteProfilePictureUrl(): TutorialGroupStudent[] {
        return this.students().map((student) => cloneWith(student, { profilePictureUrl: addPublicFilePrefix(student.profilePictureUrl) }));
    }
}
