import { Component, Input, OnInit } from '@angular/core';
import { Result, ResultService } from '../../entities/result';
import { Feedback } from '../../entities/feedback';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { BuildLogEntry } from 'app/entities/build-log';

// Modal -> Result details view
@Component({
    selector: 'jhi-editor-instructions-result-detail',
    // This popup references the result detail html template, so make sure the constant names match
    templateUrl: '../../entities/result/result-detail.component.html'
})
export class EditorInstructionsResultDetailComponent implements OnInit {
    @Input()
    result: Result;
    @Input()
    tests: string;
    isLoading: boolean;
    filterTests: string[];
    feedbackList: Feedback[];
    buildLogs: BuildLogEntry[];

    constructor(public activeModal: NgbActiveModal, private resultService: ResultService) {}

    ngOnInit(): void {
        this.filterTests = this.tests.split(',');
        if (this.result.feedbacks && this.result.feedbacks.length > 0) {
            this.feedbackList = this.result.feedbacks.filter(detail => this.filterTests.indexOf(detail.text) !== -1);
        } else {
            this.isLoading = true;
            this.resultService.getFeedbackDetailsForResult(this.result.id).subscribe(res => {
                this.feedbackList = res.body.filter(detail => this.filterTests.indexOf(detail.text) !== -1);
                this.isLoading = false;
            });
        }
        this.isLoading = false;
    }
}
