import { Component, Input, OnInit } from '@angular/core';
import { Result, ResultService } from './';
import { RepositoryService } from 'app/entities/repository';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Feedback } from '../feedback/index';
import { BuildLogEntry, BuildLogEntryArray } from 'app/entities/build-log';
import { AccountService } from 'app/core';
import { CourseService } from 'app/entities/course';

// Modal -> Result details view
@Component({
    selector: 'jhi-result-detail',
    templateUrl: './result-detail.component.html',
})
export class ResultDetailComponent implements OnInit {
    displayedResult: Result;
    isLoading: boolean;
    feedbackList: Feedback[];
    buildLogs: BuildLogEntryArray;
    isAtLeastTutor: boolean;

    constructor(
        public activeModal: NgbActiveModal,
        private resultService: ResultService,
        private repositoryService: RepositoryService,
        private accountService: AccountService,
        private courseService: CourseService,
    ) {}

    ngOnInit(): void {
        if (this.displayedResult.feedbacks && this.displayedResult.feedbacks.length > 0) {
            // make sure to reuse existing feedback items and to load feedback at most only once when this component is opened
            this.feedbackList = this.displayedResult.feedbacks;
            return;
        }
        this.isLoading = true;
        this.resultService.getFeedbackDetailsForResult(this.displayedResult.id).subscribe(res => {
            this.displayedResult.feedbacks = res.body!;
            this.feedbackList = res.body!;
            if (!this.feedbackList || this.feedbackList.length === 0) {
                // If we don't have received any feedback, we fetch the buid log outputs
                this.repositoryService.buildlogs(this.displayedResult.participation!.id).subscribe((repoResult: BuildLogEntry[]) => {
                    this.buildLogs = new BuildLogEntryArray(...repoResult);
                    this.isLoading = false;
                });
            } else {
                this.isLoading = false;
            }
        });
        this.isLoading = false;
    }

    @Input()
    set result(result: Result) {
        this.displayedResult = result;
        this.courseService.findWithBasicInformation(this.displayedResult.id).subscribe(resp => (this.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(resp.body!)));
    }
}
