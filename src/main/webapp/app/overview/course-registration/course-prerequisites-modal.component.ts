import { Component, Input, OnInit } from '@angular/core';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { ActivatedRoute } from '@angular/router';
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

    constructor(
        private activatedRoute: ActivatedRoute,
        private alertService: AlertService,
        private activeModal: NgbActiveModal,
        private learningGoalService: LearningGoalService,
    ) {}

    ngOnInit(): void {
        if (this.courseId) {
            this.loadData();
        }
    }

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

    identify(index: number, learningGoal: LearningGoal) {
        return `${index}-${learningGoal.id}`;
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }
}
