import { Component, OnDestroy, OnInit } from '@angular/core';

import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { TextSubmission } from 'app/entities/text-submission';
import { TextExercise, TextExerciseService } from 'app/entities/text-exercise';
import { Result } from 'app/entities/result';
import { Participation, ParticipationService } from 'app/entities/participation';

@Component({
    templateUrl: './text.component.html',
    providers: [ParticipationService]
})
export class TextComponent implements OnInit, OnDestroy {
    private subscription: Subscription;
    private id: number;
    private submission: TextSubmission;
    private textExercise: TextExercise;
    participation: Participation;
    result: Result;

    constructor(
        private route: ActivatedRoute,
        private textExerciseService: TextExerciseService,
        private participationService: ParticipationService,
        private jhiAlertService: JhiAlertService
    ) {}

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            if (params['participationId']) {
                this.textExerciseService.find(params['participationId']).subscribe(
                    data => {
                        let exercise = data.body;
                        console.log(data);
                    },
                    (error: HttpErrorResponse) => this.onError(error)
                );
            }
        });
    }

    ngOnDestroy() {}

    init() {
        this.participationService.findParticipation(1, this.id).subscribe(
            (response: HttpResponse<Participation>) => {
                this.applyParticipationFull(response.body);
            },
            (res: HttpErrorResponse) => this.onError(res)
        );
    }

    applyParticipationFull(participation: Participation) {
        this.applyTextFull(participation.exercise as TextExercise);

        // apply submission if it exists
        if (participation.results.length) {
            this.submission = participation.results[0].submission as TextSubmission;
            //
            //     // update submission time
            //     this.updateSubmissionTime();
            //
            //     // show submission answers in UI
            //     this.applySubmission();
            //
            //     if (participation.results[0].resultString && this.quizExercise.ended) {
            //         // quiz has ended and results are available
            //         this.showResult(participation.results);
            //     }
        } else {
            this.submission = new TextSubmission();
        }
    }

    applyTextFull(textExercise: TextExercise) {
        this.textExercise = textExercise;
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message, null, null);
    }
}
