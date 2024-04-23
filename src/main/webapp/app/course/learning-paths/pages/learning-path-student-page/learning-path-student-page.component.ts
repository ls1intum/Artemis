import { Component } from '@angular/core';
import { faCheckCircle, faChevronDown } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-learning-path-student-page',
    templateUrl: './learning-path-student-page.component.html',
    styleUrl: './learning-path-student-page.component.scss',
})
export class LearningPathStudentPageComponent {
    protected readonly faChevronDown = faChevronDown;
    protected readonly faCheckCircle = faCheckCircle;
}
