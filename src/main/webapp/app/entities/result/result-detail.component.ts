import { Component, Input, OnInit } from '@angular/core';
import { Result, ResultService } from './';
import { RepositoryService } from '../repository/repository.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Feedback } from '../feedback/index';

// Modal -> Result details view
@Component({
    selector: 'jhi-result-detail',
    templateUrl: './result-detail.component.html'
})
export class ResultDetailComponent implements OnInit {
    @Input() result: Result;
    isLoading: boolean;
    feedbackList: Feedback[];
    buildLogs;

    constructor(public activeModal: NgbActiveModal,
                private resultService: ResultService,
                private repositoryService: RepositoryService) {}

    ngOnInit(): void {
        this.isLoading = true;
        this.resultService.getFeedbackDetailsForResult(this.result.id).subscribe(res => {
            this.feedbackList = res.body;
            if (!this.feedbackList || this.feedbackList.length === 0) {
                // If we don't have received any feedback, we fetch the buid log outputs
                this.repositoryService.buildlogs(this.result.participation.id).subscribe(repoResult => {
                    this.buildLogs = repoResult;
                    this.isLoading = false;
                });
            } else {
                this.isLoading = false;
            }
        });
        this.isLoading = false;
    }
}
