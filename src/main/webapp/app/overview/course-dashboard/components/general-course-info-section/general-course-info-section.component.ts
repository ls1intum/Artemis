import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faFloppyDisk, faSpinner, faWrench } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseDashboardService } from 'app/overview/course-dashboard/course-dashboard.service';
import { AlertService } from 'app/core/util/alert.service';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';

@Component({
    selector: 'jhi-general-course-info-section',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, FontAwesomeModule, ArtemisMarkdownModule, ArtemisMarkdownEditorModule],
    templateUrl: './general-course-info-section.component.html',
    styleUrl: './general-course-info-section.component.scss',
})
export class GeneralCourseInfoSectionComponent {
    protected readonly MarkdownEditorHeight = MarkdownEditorHeight;

    protected readonly faSpinner = faSpinner;
    protected readonly faWrench = faWrench;
    protected readonly faFloppyDisk = faFloppyDisk;

    private readonly courseDashboardService = inject(CourseDashboardService);
    private readonly alertService = inject(AlertService);
    private readonly courseStorageService = inject(CourseStorageService);

    readonly courseId = input.required<number>();
    readonly irisEnabled = input.required<boolean>();

    readonly course = computed(() => this.courseStorageService.getCourse(this.courseId()));
    readonly isAtLeastInstructor = computed(() => !!this.course()?.isAtLeastInstructor);
    readonly isEditMode = signal<boolean>(false);
    readonly isLoading = signal<boolean>(true);
    readonly generalCourseInformation = signal<string | undefined>(undefined);
    readonly courseInformationCopy = signal<string | undefined>(undefined);

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            untracked(() => this.loadGeneralCourseInformation(courseId));
        });
    }

    public toggleEditMode() {
        this.isEditMode.set(!this.isEditMode());
    }

    public cancelEdit() {
        this.generalCourseInformation.set(this.courseInformationCopy());
        this.toggleEditMode();
    }

    public updateGeneralInformation(updatedInformation: string) {
        this.generalCourseInformation.set(updatedInformation);
    }

    public async saveGeneralInformation() {
        try {
            this.isLoading.set(true);
            await this.courseDashboardService.updateGeneralCourseInformation(this.courseId(), this.generalCourseInformation() ?? '');
        } catch (error) {
            this.alertService.error(error);
        } finally {
            this.courseInformationCopy.set(this.generalCourseInformation());
            this.isLoading.set(false);
            this.toggleEditMode();
        }
    }

    protected async loadGeneralCourseInformation(courseId: number) {
        try {
            this.isLoading.set(true);
            const generalCourseInformation = await this.courseDashboardService.getGeneralCourseInformation(courseId);
            this.generalCourseInformation.set(generalCourseInformation);
            this.courseInformationCopy.set(generalCourseInformation);
        } catch (error) {
            this.alertService.error(error);
        } finally {
            this.isLoading.set(false);
        }
    }
}
