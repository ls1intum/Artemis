import { Component, Input, OnInit, inject } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { finalize } from 'rxjs/operators';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { PrerequisiteService } from 'app/course/competencies/prerequisite.service';
import { Prerequisite } from 'app/entities/prerequisite.model';

@Component({
    selector: 'jhi-course-prerequisites-modal',
    templateUrl: './course-prerequisites-modal.component.html',
    styles: [],
    standalone: false,
})
export class CoursePrerequisitesModalComponent implements OnInit {
    private alertService = inject(AlertService);
    private activeModal = inject(NgbActiveModal);
    private prerequisiteService = inject(PrerequisiteService);

    @Input()
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
