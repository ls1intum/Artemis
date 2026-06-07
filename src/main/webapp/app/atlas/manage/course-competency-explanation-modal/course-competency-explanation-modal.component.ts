import { Component, inject } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-course-competency-explanation-modal',
    imports: [TranslateDirective, FontAwesomeModule],
    templateUrl: './course-competency-explanation-modal.component.html',
    styleUrl: './course-competency-explanation-modal.component.scss',
})
export class CourseCompetencyExplanationModalComponent {
    protected readonly closeIcon = faXmark;

    protected readonly DOCUMENTATION_LINK = 'https://docs.artemis.tum.de/instructor/adaptive-learning';

    private readonly dialogRef = inject(DynamicDialogRef);

    protected closeModal(): void {
        this.dialogRef.close();
    }
}
