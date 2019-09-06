import { Component, OnInit, Input, Output, EventEmitter } from '@angular/core';
import { Complaint, ComplaintType } from 'app/entities/complaint';
import { AccountService } from 'app/core';
import { Result } from 'app/entities/result';
import { Conflict } from 'app/modeling-assessment-editor/conflict.model';
import { ComplaintResponse } from 'app/entities/complaint-response';

@Component({
    selector: 'jhi-assessment-layout',
    templateUrl: './assessment-layout.component.html',
    styleUrls: ['./assessment-layout.component.scss'],
})
export class AssessmentLayoutComponent implements OnInit {
    @Output() navigateBack = new EventEmitter<void>();

    @Input() isLoading: boolean;
    @Input() busy: boolean;

    @Input() isAssessor: boolean;
    isAtLeastInstructor: boolean;
    @Input() canOverride: boolean;

    @Input() result: Result | null;
    @Input() conflicts: Conflict[] | null;
    @Input() assessmentsAreValid: boolean;
    ComplaintType = ComplaintType;
    @Input() complaint: Complaint;

    @Output() save = new EventEmitter<void>();
    @Output() submit = new EventEmitter<void>();
    @Output() cancel = new EventEmitter<void>();
    @Output() resolveConflict = new EventEmitter<void>();
    @Output() nextSubmission = new EventEmitter<void>();
    @Output() updateAssessmentAfterComplaint = new EventEmitter<ComplaintResponse>();

    constructor(private accountService: AccountService) {}

    ngOnInit() {
        this.isAtLeastInstructor = this.accountService.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR']);
    }
}
