import { Component, Input, OnInit } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, filter, map, switchMap, tap } from 'rxjs/operators';
import { Result, ResultService } from '../../entities/result';
import { Feedback } from '../../entities/feedback';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { BuildLogEntryArray } from 'app/entities/build-log';

// Modal -> Result details view
@Component({
    selector: 'jhi-code-editor-instructions-result-detail',
    // This popup references the result detail html template, so make sure the constant names match
    templateUrl: '../../entities/result/result-detail.component.html',
})
export class EditorInstructionsResultDetailComponent implements OnInit {
    @Input() result: Result;
    @Input() tests: string;
    isLoading: boolean;
    filterTests: string[];
    feedbackList: Feedback[];
    buildLogs: BuildLogEntryArray;

    constructor(public activeModal: NgbActiveModal, private resultService: ResultService) {}

    ngOnInit(): void {
        this.filterTests = this.tests.split(',');
        of(this.result.feedbacks)
            .pipe(
                switchMap(feedbacks => (feedbacks ? of(feedbacks) : this.loadResultDetails(this.result))),
                map(feedbacks =>
                    this.filterTests.map(test => {
                        const matchingFeedback = feedbacks.find(({ text }) => text === test);
                        return matchingFeedback || ({ text: test, detailText: 'No result information available', type: 'AUTOMATIC' } as Feedback);
                    }),
                ),
                tap(feedbacks => (this.feedbackList = feedbacks)),
            )
            .subscribe();
    }

    loadResultDetails(result: Result): Observable<Feedback[]> {
        this.isLoading = true;
        return this.resultService.getFeedbackDetailsForResult(result.id).pipe(
            filter(res => !!res && !!res.body),
            map(res => res.body as Feedback[]),
            catchError(() => of([] as Feedback[])),
            tap(() => (this.isLoading = false)),
        );
    }
}
