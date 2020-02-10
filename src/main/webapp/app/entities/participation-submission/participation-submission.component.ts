import { Component, Input, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SubmissionService } from 'app/entities/submission/submission.service';

import { JhiEventManager } from 'ng-jhipster';
import { Subscription } from 'rxjs/Subscription';
import { catchError, map } from 'rxjs/operators';
import { of } from 'rxjs';
import { StudentParticipation } from 'app/entities/participation';
import { ParticipationService } from 'app/entities/participation/participation.service';
import { Submission } from 'app/entities/submission';

@Component({
    selector: 'jhi-participation-submission',
    templateUrl: './participation-submission.component.html',
})
export class ParticipationSubmissionComponent implements OnInit {
    @Input() participationId: number;
    participation: StudentParticipation;
    submissions: Submission[];
    eventSubscriber: Subscription;
    isLoading = true;

    constructor(
        private route: ActivatedRoute,
        private submissionService: SubmissionService,
        private participationService: ParticipationService,
        private eventManager: JhiEventManager,
    ) {}

    ngOnInit() {
        this.setupPage();
        this.eventSubscriber = this.eventManager.subscribe('submissionsModification', () => this.setupPage());
    }

    setupPage() {
        this.route.params.subscribe(params => {
            this.participationId = +params['participationId'];
        });

        this.participationService
            .find(this.participationId)
            .pipe(
                map(({ body }) => body),
                catchError(() => of(null)),
            )
            .subscribe(participation => {
                if (participation) {
                    this.participation = participation;
                    this.isLoading = false;
                }
            });

        this.isLoading = true;

        this.submissionService
            .findAllSubmissionsOfParticipation(this.participationId)
            .pipe(
                map(({ body }) => body),
                catchError(() => of([])),
            )
            .subscribe(submissions => {
                if (submissions) {
                    this.submissions = submissions;
                    this.isLoading = false;
                }
            });
    }
}
