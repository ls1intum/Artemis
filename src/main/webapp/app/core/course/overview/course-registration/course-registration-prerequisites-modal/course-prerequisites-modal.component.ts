import { Component, effect, inject, input, model, untracked } from '@angular/core';
import { AlertService } from 'app/shared/service/alert.service';
import { finalize } from 'rxjs/operators';
import { PrerequisiteService } from 'app/atlas/manage/services/prerequisite.service';
import { Prerequisite } from 'app/atlas/shared/entities/prerequisite.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CompetencyCardComponent } from 'app/atlas/overview/competency-card/competency-card.component';
import { DialogModule } from 'primeng/dialog';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-course-prerequisites-modal',
    templateUrl: './course-prerequisites-modal.component.html',
    imports: [TranslateDirective, CompetencyCardComponent, DialogModule, ArtemisTranslatePipe],
})
export class CoursePrerequisitesModalComponent {
    private alertService = inject(AlertService);
    private prerequisiteService = inject(PrerequisiteService);

    readonly visible = model<boolean>(false);
    readonly courseId = input.required<number>();

    isLoading = false;
    prerequisites: Prerequisite[] = [];

    constructor() {
        effect(() => {
            if (this.visible()) {
                untracked(() => this.loadData());
            }
        });
    }

    /**
     * Loads the prerequisites for the specified course id
     * Displays an error alert if it fails
     */
    loadData() {
        this.isLoading = true;
        this.prerequisiteService
            .getAllForCourse(this.courseId())
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (prerequisites) => {
                    this.prerequisites = prerequisites.body ?? [];
                },
                error: (error: string) => {
                    this.alertService.error(error);
                },
            });
    }

    /**
     * Closes the dialog
     */
    clear() {
        this.visible.set(false);
    }
}
