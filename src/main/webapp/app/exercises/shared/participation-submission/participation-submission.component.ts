import { Component, Input, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';

import { JhiEventManager } from 'ng-jhipster';
import { Subscription } from 'rxjs/Subscription';
import { catchError, map } from 'rxjs/operators';
import { of } from 'rxjs';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { Submission } from 'app/entities/submission.model';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

@Component({
    selector: 'jhi-participation-submission',
    templateUrl: './participation-submission.component.html',
})
export class ParticipationSubmissionComponent implements OnInit {
    @Input() participationId: number;
    participation: Participation;
    submissions: Submission[];
    eventSubscriber: Subscription;
    isLoading = true;

    constructor(
        private route: ActivatedRoute,
        private submissionService: SubmissionService,
        private participationService: ParticipationService,
        private eventManager: JhiEventManager,
    ) {}

    /**
     * Initialize component by setting up page and subscribe to eventManager
     */
    ngOnInit() {
        this.setupPage();
        this.eventSubscriber = this.eventManager.subscribe('submissionsModification', () => this.setupPage());
    }

    /**
     * Set up page by loading participation and all submissions
     */
    setupPage() {
        this.route.params.subscribe((params) => {
            this.participationId = +params['participationId'];
        });

        this.participationService
            .find(this.participationId)
            .pipe(
                map(({ body }) => body),
                catchError(() => of(null)),
            )
            .subscribe((participation) => {
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
            .subscribe((submissions) => {
                if (submissions) {
                    this.submissions = submissions;
                    this.isLoading = false;
                }
            });
    }

    // TODO: also display commit hash

    getName() {
        if (this.participation.type === ParticipationType.STUDENT) {
            return (this.participation as StudentParticipation).student?.name || (this.participation as StudentParticipation).team?.name;
        } else if (this.participation.type === ParticipationType.SOLUTION) {
            return 'Solution Participation'; // TODO: translation string
        } else if (this.participation.type === ParticipationType.TEMPLATE) {
            return 'Template Participation'; // TODO: translation string
        } else {
            return 'Unknown';
        }
    }
}
