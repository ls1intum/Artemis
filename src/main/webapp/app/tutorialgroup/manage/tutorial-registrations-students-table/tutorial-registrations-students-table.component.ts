import { Component, computed, input } from '@angular/core';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { addPublicFilePrefix } from 'app/app.constants';
import { TutorialGroupStudent } from 'app/openapi/model/tutorialGroupStudent';

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
        return this.students().map((student) => ({ ...student, profilePictureUrl: addPublicFilePrefix(student.profilePictureUrl) }));
    }
}
