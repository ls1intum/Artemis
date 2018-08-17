import {Component, Input, OnInit} from '@angular/core';
import {Result, ResultService} from '../../entities/result';
import {Feedback} from '../../entities/feedback';
import {NgbActiveModal} from '@ng-bootstrap/ng-bootstrap';

// Modal -> Result details view
@Component({
    selector: 'jhi-editor-instructions-result-detail',
    templateUrl: '../../courses/results/result-detail.html'
})
export class EditorInstructionsResultDetailComponent implements OnInit {
    @Input() result: Result;
    @Input() tests;
    loading: boolean;
    filterTests: string;
    details: Feedback[];
    buildLogs;

    constructor(public activeModal: NgbActiveModal,
                private resultService: ResultService) {}

    ngOnInit(): void {
        this.loading = true;
        this.filterTests = this.tests.split(',');
        this.resultService.details(this.result.id).subscribe(res => {
            this.details = res.body.filter(
                detail => this.filterTests.indexOf(detail.text) !== -1
            );
            this.loading = false;
        });
        this.loading = false;
    }
}
