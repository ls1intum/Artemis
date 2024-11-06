import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseDashboardService } from 'app/overview/course-dashboard/course-dashboard.service';
import { AlertService } from 'app/core/util/alert.service';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

@Component({
    selector: 'jhi-general-course-info-section',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, FontAwesomeModule, ArtemisMarkdownModule],
    templateUrl: './general-course-info-section.component.html',
    styleUrl: './general-course-info-section.component.scss',
})
export class GeneralCourseInfoSectionComponent {
    protected readonly faSpinner = faSpinner;

    private readonly courseDashboardService = inject(CourseDashboardService);
    private readonly alertService = inject(AlertService);

    readonly courseId = input.required<number>();

    readonly isLoading = signal<boolean>(true);
    readonly generalCourseInformation = signal<string | undefined>(undefined);

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            untracked(() => this.loadGeneralCourseInformation(courseId));
        });
    }

    protected async loadGeneralCourseInformation(courseId: number) {
        try {
            this.isLoading.set(true);
            const generalCourseInformation = await this.courseDashboardService.getGeneralCourseInformation(courseId);
            this.generalCourseInformation.set(generalCourseInformation);
        } catch (error) {
            this.alertService.error(error);
        } finally {
            this.isLoading.set(false);
        }
    }
}
