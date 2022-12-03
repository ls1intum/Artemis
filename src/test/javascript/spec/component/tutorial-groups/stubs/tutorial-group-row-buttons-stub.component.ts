import { Component, EventEmitter, Output } from '@angular/core';
import { Input } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { Course } from 'app/entities/course.model';

@Component({ selector: 'jhi-tutorial-group-row-buttons', template: '' })
export class TutorialGroupRowButtonsStubComponent {
    @Input() isAtLeastInstructor = false;
    @Input() tutorialGroup: TutorialGroup;
    @Input() course: Course;

    @Output() tutorialGroupDeleted = new EventEmitter<void>();
    @Output() registrationsChanged = new EventEmitter<void>();
}
