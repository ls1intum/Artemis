import { Component, input } from '@angular/core';
import { TutorialRegistrationsStudentsTableRemoveActionColumnInfo } from 'app/tutorialgroup/manage/tutorial-registrations-students-table/tutorial-registrations-students-table.component';
import { TutorialGroupRegisteredStudentDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';

@Component({
    selector: 'jhi-tutorial-registrations-students-table',
    template: '',
})
export class TutorialRegistrationsStudentsTableStubComponent {
    students = input.required<TutorialGroupRegisteredStudentDTO[]>();
    removeActionColumnInfo = input<TutorialRegistrationsStudentsTableRemoveActionColumnInfo>();
}
