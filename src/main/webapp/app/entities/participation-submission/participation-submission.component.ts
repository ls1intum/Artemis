import { Component, OnInit, Input } from '@angular/core';
import { ParticipationService, StudentParticipation } from 'app/entities/participation';
import { ActivatedRoute } from '@angular/router';
import { Submission } from 'app/entities/submission';
import { SubmissionService } from 'app/entities/submission/submission.service';
import { Result } from 'app/entities/result';

import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { Subscription } from 'rxjs/Subscription';

@Component({
    selector: 'jhi-participation-submission',
    templateUrl: './participation-submission.component.html',
})
export class ParticipationSubmissionComponent implements OnInit {
    @Input() participationId: number;
    submissions: Submission[];
    eventSubscriber: Subscription;
    result: Result;

    constructor(
        private route: ActivatedRoute,
        private participationService: ParticipationService,
        private submissionService: SubmissionService,
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

        this.submissionService.findAllSubmissionsOfParticipation(this.participationId).subscribe(response => {
            this.submissions = response.body!;
        });
    }

    /*
    deleteSubmission(submissionId: number) {
        console.log(this.submissions);

        this.submissionService.delete(submissionId).subscribe(() => {
            this.eventManager.broadcast({
                name: 'submissionsModification',
                content: 'Deleted an submission',
            });
        });
    }
    */
}
