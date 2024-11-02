import { ChangeDetectionStrategy, Component, effect, input, signal, untracked } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-general-course-info-section',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, FontAwesomeModule],
    templateUrl: './general-course-info-section.component.html',
    styleUrl: './general-course-info-section.component.scss',
})
export class GeneralCourseInfoSectionComponent {
    protected readonly faSpinner = faSpinner;

    readonly courseId = input.required<number>();

    readonly isLoading = signal<boolean>(true);

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            untracked(() => this.loadGeneralCourseInformation(courseId));
        });
    }

    private loadGeneralCourseInformation(courseId: number) {
        console.log(`load general information ${courseId}`);
    }
}
