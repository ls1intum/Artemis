import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Conflict, ConflictingResult } from 'app/modeling-assessment-editor/conflict.model';
import { ActivatedRoute, Router } from '@angular/router';
import { ModelingSubmissionService } from 'app/entities/modeling-submission';
import { ModelingAssessmentService } from 'app/entities/modeling-assessment';
import { JhiAlertService } from 'ng-jhipster';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Feedback } from 'app/entities/feedback';
import { ConflictEscalationModalComponent } from 'app/modeling-assessment-conflict/conflict-escalation-modal/conflict-escalation-modal.component';
import { User } from 'app/core';

@Component({
    selector: 'jhi-escalated-conflict-resolution',
    templateUrl: './escalated-conflict-resolution.component.html',
    styles: [],
})
export class EscalatedConflictResolutionComponent implements OnInit {
    private resultId: number;
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
            this.resultId = Number(params['submissionId']);
            this.modelingAssessmentService.getConflicts(this.submissionId).subscribe(
                conflicts => {
                    this.conflicts = conflicts;
                },
                error => {
                    this.jhiAlertService.error('modelingAssessmentConflict.messages.noConflicts');
                },
            );
        });
    }

    onSave(newFeedbacks: Feedback[]) {}

    onEscalate(emitted: { escalatedConflicts: Conflict[]; newFeedbacks: Feedback[] }) {}

    onSubmit(newFeedbacks: Feedback[]) {}
}
