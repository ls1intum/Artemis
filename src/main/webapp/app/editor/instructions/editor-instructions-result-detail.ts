import { Component, Input, OnInit } from '@angular/core';
import { Result, ResultService } from '../../entities/result';
import { Feedback } from '../../entities/feedback';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

// Modal -> Result details view
@Component({
    selector: 'jhi-editor-instructions-result-detail',
    // This popup references the result detail html template, so make sure the constant names match
    templateUrl: '../../entities/result/result-detail.component.html'
})
export class EditorInstructionsResultDetailComponent implements OnInit {
    @Input() result: Result;
    @Input() tests: string;
    isLoading: boolean;
    filterTests: string[];
    feedbackList: Feedback[];
    buildLogs;

    constructor(public activeModal: NgbActiveModal,
                private resultService: ResultService) {}

    ngOnInit(): void {
        this.isLoading = true;
        this.filterTests = this.tests.split(',');
        this.resultService.details(this.result.id).subscribe(res => {
            this.feedbackList = res.body.filter(
                detail => this.filterTests.indexOf(detail.text) !== -1
            );
            this.isLoading = false;
        });
        this.isLoading = false;
    }
}
