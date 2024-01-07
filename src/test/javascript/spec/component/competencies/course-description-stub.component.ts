import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({ selector: 'jhi-course-description', template: '', standalone: true })
export class CourseDescriptionStubComponent {
    @Input() isLoading = false;
    @Output() formSubmitted: EventEmitter<string> = new EventEmitter<string>();
}
