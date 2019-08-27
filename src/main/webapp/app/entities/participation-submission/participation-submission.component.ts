import { Component, OnInit, Input } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SubmissionService } from 'app/entities/submission/submission.service';

import { JhiEventManager } from 'ng-jhipster';
import { Subscription } from 'rxjs/Subscription';
import { catchError, map } from 'rxjs/operators';
import { of } from 'rxjs';

@Component({
    selector: 'jhi-participation-submission',
    templateUrl: './participation-submission.component.html',
})
export class ParticipationSubmissionComponent implements OnInit {
    @Input() participationId: number;
    submissions: any;
    eventSubscriber: Subscription;
    isLoading = true;

    constructor(private route: ActivatedRoute, private submissionService: SubmissionService, private eventManager: JhiEventManager) {}

    ngOnInit() {
        this.setupPage();
        this.eventSubscriber = this.eventManager.subscribe('submissionsModification', () => this.setupPage());
    }

    setupPage() {
        this.route.params.subscribe(params => {
            this.participationId = +params['participationId'];
        });

        this.submissionService
            .findAllSubmissionsOfParticipation(this.participationId)
            .pipe(
                map(({ body }) => body),
                catchError(() => of([])),
            )
            .subscribe(submissions => {
                this.submissions = submissions;
                this.isLoading = false;
            });
    }
}
