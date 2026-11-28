import { Component, inject } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiDialogRef } from '@tumaet/ui-angular/dialog';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-course-competency-explanation-modal',
    imports: [TranslateDirective, FaIconComponent, TumAetUiButtonDirective, ArtemisTranslatePipe],
    templateUrl: './course-competency-explanation-modal.component.html',
})
export class CourseCompetencyExplanationModalComponent {
    private readonly dialogRef = inject(TumAetUiDialogRef);

    protected readonly closeIcon = faXmark;

    protected readonly DOCUMENTATION_LINK = 'https://docs.artemis.tum.de/instructor/analytics/adaptive-learning';

    protected closeModal(): void {
        this.dialogRef.close();
    }
}
