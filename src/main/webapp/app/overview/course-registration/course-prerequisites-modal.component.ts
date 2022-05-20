import { Component, Input, OnInit } from '@angular/core';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { AlertService } from 'app/core/util/alert.service';
import { finalize } from 'rxjs/operators';
import { LearningGoal } from 'app/entities/learningGoal.model';
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
    prerequisites: LearningGoal[] = [];

    constructor(private alertService: AlertService, private activeModal: NgbActiveModal, private learningGoalService: LearningGoalService) {}

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
        this.learningGoalService
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
     * Calculates a unique identity for each learning goal card shown in the component
     * @param index The index in the list
     * @param learningGoal The learning goal of the current iteration
     */
    identify(index: number, learningGoal: LearningGoal) {
        return `${index}-${learningGoal.id}`;
    }

    /**
     * Dismisses the currently active modal
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }
}
