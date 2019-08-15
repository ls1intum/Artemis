import { Component, OnInit } from '@angular/core';
import { ParticipationService, StudentParticipation } from 'app/entities/participation';
import { ExerciseService } from 'app/entities/exercise';
import { ActivatedRoute } from '@angular/router';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

@Component({
    selector: 'jhi-participation-submission',
    templateUrl: './participation-submission.component.html',
})
export class ParticipationSubmissionComponent implements OnInit {
    participation: StudentParticipation;

    constructor(private activatedRoute: ActivatedRoute) {
        this.participation = JSON.parse(activatedRoute.snapshot.params['participation']);
    }

    ngOnInit() {}
}
