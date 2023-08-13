import { Component, Input, OnInit } from '@angular/core';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { AlertService } from 'app/core/util/alert.service';
import { finalize } from 'rxjs/operators';
import { Competency } from 'app/entities/competency.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-course-prerequisites-modal',
    templateUrl: './course-prerequisites-modal.component.html',
    styles: [],
})
export class CoursePrerequisitesModalComponent implements OnInit {
    @Input()
    courseId: number;

    isLoading = false;
    prerequisites: Competency[] = [];

    constructor(private alertService: AlertService, private activeModal: NgbActiveModal, private competencyService: CompetencyService) {}

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
        this.competencyService
            .getAllPrerequisitesForCourse(this.courseId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (prerequisites) => {
                    this.prerequisites = prerequisites.body!;
                },
                error: (error: string) => {
                    this.alertService.error(error);
                },
            });
    }

    /**
     * Calculates a unique identity for each competency card shown in the component
     * @param index The index in the list
     * @param competency The competency of the current iteration
     */
    identify(index: number, competency: Competency) {
        return `${index}-${competency.id}`;
    }

    /**
     * Dismisses the currently active modal
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }
}
