import { Component, input, output } from '@angular/core';

@Component({ selector: 'jhi-course-description-form', template: '' })
export class CourseDescriptionFormStubComponent {
    isLoading = input(false);
    formSubmitted = output<string>();
}
