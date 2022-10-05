import { Component, OnDestroy, OnInit } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { LtiConfiguration } from 'app/entities/lti-configuration.model';
import { ExerciseLtiConfigurationService, ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-exercise-lti-configuration',
    templateUrl: './exercise-lti-configuration.component.html',
})
export class ExerciseLtiConfigurationComponent implements OnInit, OnDestroy {
    routeSub: Subscription;
    exercise: Exercise;
    ltiConfiguration: LtiConfiguration;
    requireExistingUser = true;
    lookupUserByEmail = true;

    wasCopiedKey = '';

    constructor(private route: ActivatedRoute, private exerciseService: ExerciseService, private exerciseLtiConfigurationService: ExerciseLtiConfigurationService) {}

    /**
     * Opens the configuration for the exercise encoded in the route
     */
    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            const exerciseId = params['exerciseId'];
            if (exerciseId) {
                this.exerciseService.find(exerciseId).subscribe((exerciseResponse: HttpResponse<Exercise>) => {
                    this.exercise = exerciseResponse.body!;
                });
                this.exerciseLtiConfigurationService.find(exerciseId).subscribe((ltiConfigurationResponse: HttpResponse<any>) => {
                    this.ltiConfiguration = ltiConfigurationResponse.body!;
                });
            }
        });
    }

    /**
     * Unsubscribes from the route subscription
     */
    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }

    /**
     * Gets the formatted custom parameters
     */
    getFormattedCustomParameters(): string {
        return `require_existing_user=${this.requireExistingUser}\n` + `lookup_user_by_email=${this.lookupUserByEmail}`;
    }

    /**
     * set wasCopied for 3 seconds on success for the received key
     */
    onCopyFinished = (successful: boolean, key: string): void => {
        if (successful) {
            this.wasCopiedKey = key;
            setTimeout(() => {
                this.wasCopiedKey = '';
            }, 3000);
        }
    };
}
