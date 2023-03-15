import { Component, EventEmitter, Input, Output } from '@angular/core';

import { Course } from 'app/entities/course.model';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';

@Component({ selector: 'jhi-tutorial-group-row-buttons', template: '' })
export class TutorialGroupRowButtonsStubComponent {
    @Input() isAtLeastInstructor = false;
    @Input() tutorialGroup: TutorialGroup;
    @Input() course: Course;

    @Output() tutorialGroupDeleted = new EventEmitter<void>();
    @Output() registrationsChanged = new EventEmitter<void>();
    @Output() attendanceChanged = new EventEmitter<TutorialGroupSession>();
}
