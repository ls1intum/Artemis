import { Component, EventEmitter, Output } from '@angular/core';
import { Input } from '@angular/core';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';

@Component({ selector: 'jhi-tutorial-group-row-buttons', template: '' })
export class TutorialGroupRowButtonsStubComponent {
    @Input() isAtLeastInstructor = false;
    @Input() tutorialGroup: TutorialGroup;
    @Input() course: Course;

    @Output() tutorialGroupDeleted = new EventEmitter<void>();
    @Output() registrationsChanged = new EventEmitter<void>();
    @Output() attendanceChanged = new EventEmitter<TutorialGroupSession>();
}
