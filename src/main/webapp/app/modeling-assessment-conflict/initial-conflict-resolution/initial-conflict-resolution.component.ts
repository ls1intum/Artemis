import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ModelingSubmissionService } from 'app/entities/modeling-submission';
import { ModelingAssessmentService } from 'app/entities/modeling-assessment';
import { Conflict, ConflictingResult } from 'app/modeling-assessment-editor/conflict.model';
import { JhiAlertService } from 'ng-jhipster';
import { Feedback } from 'app/entities/feedback';
import { ConflictEscalationModalComponent } from 'app/modeling-assessment-conflict/conflict-escalation-modal/conflict-escalation-modal.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { User } from 'app/core';

@Component({
    selector: 'jhi-initial-conflict-resolution',
    templateUrl: './initial-conflict-resolution.component.html',
    styles: [],
})
export class InitialConflictResolutionComponent implements OnInit {
    private submissionId: number;
    private conflicts: Conflict[];

    @ViewChild('escalationModal') escalationModal: ElementRef;

    constructor(
        private route: ActivatedRoute,
        private modelingSubmissionService: ModelingSubmissionService,
        private modelingAssessmentService: ModelingAssessmentService,
        private jhiAlertService: JhiAlertService,
        private router: Router,
        private modalService: NgbModal,
    ) {}

    ngOnInit() {
        this.route.params.subscribe(params => {
            this.submissionId = Number(params['submissionId']);

            this.conflicts = this.modelingAssessmentService.popLocalConflicts(this.submissionId);
            if (this.conflicts && this.conflicts.length > 0) {
            } else {
                this.modelingAssessmentService.getConflicts(this.submissionId).subscribe(
                    conflicts => {
                        this.conflicts = conflicts;
                    },
                    error => {
                        this.jhiAlertService.error('modelingAssessmentConflict.messages.noConflicts');
                    },
                );
            }
        });
    }

    onSave(newFeedbacks: Feedback[]) {
        this.modelingAssessmentService.saveAssessment(newFeedbacks, this.submissionId).subscribe(
            () => {
                this.jhiAlertService.success('modelingAssessmentEditor.messages.saveSuccessful');
            },
            () => this.jhiAlertService.error('modelingAssessmentEditor.messages.saveFailed'),
        );
    }

    onEscalate(emitted: { escalatedConflicts: Conflict[]; newFeedbacks: Feedback[] }) {
        const modalRef = this.modalService.open(ConflictEscalationModalComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.tutorsEscalatingTo = this.getDistinctTutorsEscalatingTo(emitted.escalatedConflicts);
        modalRef.componentInstance.escalatedConflictsCount = emitted.escalatedConflicts.length;
        modalRef.result.then(value => {
            this.modelingAssessmentService.escalateConflict(emitted.escalatedConflicts).subscribe(() => this.onSubmit(emitted.newFeedbacks));
        });
    }

    onSubmit(newFeedbacks: Feedback[]) {
        this.modelingAssessmentService.saveAssessment(newFeedbacks, this.submissionId, true).subscribe(
            () => {
                this.jhiAlertService.success('modelingAssessmentEditor.messages.submitSuccessful');
                this.router.navigate([
                    'modeling-exercise',
                    this.conflicts[0].causingConflictingResult.result.participation.exercise.id,
                    'submissions',
                    this.submissionId,
                    'assessment',
                ]);
            },
            error => {
                if (error.status === 409) {
                    const conflicts = error.error as Conflict[];
                    this.modelingAssessmentService.convertConflicts(conflicts);
                    this.conflicts = conflicts;
                    this.jhiAlertService.clear();
                    this.jhiAlertService.error('modelingAssessmentEditor.messages.submitFailedWithConflict');
                } else {
                    this.jhiAlertService.clear();
                    this.jhiAlertService.error('modelingAssessmentEditor.messages.submitFailed');
                }
            },
        );
    }

    private getDistinctTutorsEscalatingTo(escalatedConflicts: Conflict[]): User[] {
        const distinctTutors: Map<number, User> = new Map<number, User>();
        escalatedConflicts.forEach((conflict: Conflict) => {
            conflict.resultsInConflict.forEach((conflictingResult: ConflictingResult) =>
                distinctTutors.set(conflictingResult.result.assessor.id, conflictingResult.result.assessor),
            );
        });
        return Array.from(distinctTutors.values());
    }
}
