import { Component, OnInit, inject } from '@angular/core';
import { AlertService } from 'app/shared/service/alert.service';
import { finalize } from 'rxjs/operators';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { PrerequisiteService } from 'app/atlas/manage/services/prerequisite.service';
import { Prerequisite } from 'app/atlas/shared/entities/prerequisite.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CompetencyCardComponent } from 'app/atlas/overview/competency-card/competency-card.component';

@Component({
    selector: 'jhi-course-prerequisites-modal',
    templateUrl: './course-prerequisites-modal.component.html',
    imports: [TranslateDirective, CompetencyCardComponent],
})
export class CoursePrerequisitesModalComponent implements OnInit {
    private alertService = inject(AlertService);
    private activeModal = inject(NgbActiveModal);
    private prerequisiteService = inject(PrerequisiteService);

    // Not using input() because this component is opened programmatically via NgbModal
    // and courseId is set imperatively via modalRef.componentInstance.courseId = value
    courseId: number;

    isLoading = false;
    prerequisites: Prerequisite[] = [];

    ngOnInit(): void {
        if (this.courseId) {
            this.loadData();
        }
    }

    /**
     * Loads the prerequisites for the specified course id
     * Displays an error alert if it fails
     */
    loadData() {
        this.isLoading = true;
        this.prerequisiteService
            .getAllForCourse(this.courseId)
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
     * Dismisses the currently active modal
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }
}
