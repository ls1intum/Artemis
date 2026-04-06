import { Component, input } from '@angular/core';
import { TutorialRegistrationsStudentsTableRemoveActionColumnInfo } from 'app/tutorialgroup/manage/tutorial-registrations-students-table/tutorial-registrations-students-table.component';
import { TutorialGroupStudent } from 'app/openapi/model/tutorialGroupStudent';

@Component({
    selector: 'jhi-tutorial-registrations-students-table',
    template: '',
})
export class TutorialRegistrationsStudentsTableMockComponent {
    students = input.required<TutorialGroupStudent[]>();
    removeActionColumnInfo = input<TutorialRegistrationsStudentsTableRemoveActionColumnInfo>();

    triggerRemove(student: TutorialGroupStudent, event = new Event('click')) {
        this.removeActionColumnInfo()?.onRemove(event, student);
    }
}
