import { Component, OnInit, Input } from '@angular/core';
import { ParticipationService, StudentParticipation } from 'app/entities/participation';
import { ExerciseService } from 'app/entities/exercise';
import { ActivatedRoute } from '@angular/router';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

@Component({
    selector: 'jhi-participation-submission',
    templateUrl: './participation-submission.component.html',
})
export class ParticipationSubmissionComponent implements OnInit {
    @Input() participation: any;

    constructor(private route: ActivatedRoute) {}

    ngOnInit() {
        this.route.params.subscribe(params => {
            this.participation = params['participationId'];
        });

        console.log('test');
        console.log(this.participation);
    }
}
