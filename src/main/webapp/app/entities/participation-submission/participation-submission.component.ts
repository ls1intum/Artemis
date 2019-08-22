import { Component, OnInit, Input } from '@angular/core';
import { ParticipationService, StudentParticipation } from 'app/entities/participation';
import { ExerciseService } from 'app/entities/exercise';
import { ActivatedRoute } from '@angular/router';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { Submission } from 'app/entities/submission';

@Component({
    selector: 'jhi-participation-submission',
    templateUrl: './participation-submission.component.html',
})
export class ParticipationSubmissionComponent implements OnInit {
    @Input() participationId: number;
    submissions: Submission[];
    participation: StudentParticipation;

    constructor(private route: ActivatedRoute, private participationService: ParticipationService) {}

    ngOnInit() {
        this.route.params.subscribe(params => {
            this.participationId = +params['participationId'];
            console.log(params);
        });

        this.participationService.find(this.participationId).subscribe(participationsResponse => {
            this.participation = participationsResponse.body!;
            this.submissions = this.participation.submissions;
            console.log(participationsResponse);
        });

        this.participationService.findAllSubmissionsOfParticipation(this.participationId).subscribe(response => {
            this.submissions = response;
            console.log('submissions:');
            console.log(response);
        });

        console.log('test');
        console.log(this.participationId);
        console.log(this.participation);
    }
}
