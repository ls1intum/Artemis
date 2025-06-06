import { Component, EventEmitter, Input, Output } from '@angular/core';
import { TutorialGroupSessionFormData } from 'app/tutorialgroup/manage/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { Course } from 'app/core/course/shared/entities/course.model';

@Component({ selector: 'jhi-tutorial-group-session-form', template: '' })
export class TutorialGroupSessionFormStubComponent {
    @Input() timeZone: string;
    @Input() isEditMode = false;
    @Input() course?: Course;
    @Input() formData: TutorialGroupSessionFormData;
    @Output() formSubmitted: EventEmitter<TutorialGroupSessionFormData> = new EventEmitter<TutorialGroupSessionFormData>();
}
