import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({ selector: 'jhi-course-description-form', template: '', standalone: true })
export class CourseDescriptionFormStubComponent {
    @Input() isLoading = false;
    @Output() formSubmitted: EventEmitter<string> = new EventEmitter<string>();
}
