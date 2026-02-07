import { Component, input } from '@angular/core';
import { NgClass } from '@angular/common';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { addPublicFilePrefix } from 'app/app.constants';
import { TutorialGroupRegisteredStudentDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';

export interface TutorialRegistrationsStudentsTableRemoveActionColumnInfo {
    headerStringKey: string;
    onRemove: (event: Event, student: TutorialGroupRegisteredStudentDTO) => void;
}

@Component({
    selector: 'jhi-tutorial-registrations-students-table',
    imports: [ProfilePictureComponent, TranslateDirective, NgClass],
    templateUrl: './tutorial-registrations-students-table.component.html',
    styleUrl: './tutorial-registrations-students-table.component.scss',
})
export class TutorialRegistrationsStudentsTableComponent {
    protected readonly addPublicFilePrefix = addPublicFilePrefix;

    students = input.required<TutorialGroupRegisteredStudentDTO[]>();
    removeActionColumnInfo = input<TutorialRegistrationsStudentsTableRemoveActionColumnInfo>();
}
