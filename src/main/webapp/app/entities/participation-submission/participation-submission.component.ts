import { Component, OnInit, Input } from '@angular/core';
import { ParticipationService, StudentParticipation } from 'app/entities/participation';
import { ActivatedRoute } from '@angular/router';
import { Submission } from 'app/entities/submission';
import { SubmissionService } from 'app/entities/submission/submission.service';

@Component({
    selector: 'jhi-participation-submission',
    templateUrl: './participation-submission.component.html',
})
export class ParticipationSubmissionComponent implements OnInit {
    @Input() participationId: number;
    submissions: Submission[];

    constructor(private route: ActivatedRoute, private participationService: ParticipationService, private submissionService: SubmissionService) {}

    ngOnInit() {
        this.route.params.subscribe(params => {
            this.participationId = +params['participationId'];
        });

        this.submissionService.findAllSubmissionsOfParticipation(this.participationId).subscribe(response => {
            this.submissions = response.body!;
        });
    }

    deleteSubmission(submissionId: number) {
        this.submissionService.delete(submissionId).subscribe(response => {
            console.log('delete');
            console.log(response);
        });
    }
}
