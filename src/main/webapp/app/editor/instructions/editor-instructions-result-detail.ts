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
        // TODO: check if the split is required
        this.filterTests = this.tests.split(',');
        console.log('EditorInstructionsResultDetails => filterTests', this.filterTests);
        this.resultService.details(this.result.id).subscribe(res => {
            console.log('resultService.details', res);
            this.details = res.body.filter(
                detail => this.filterTests.indexOf(detail.text) !== -1
            );
            console.log('this.details', this.details);
            this.loading = false;
        });
        this.loading = false;
    }
}
