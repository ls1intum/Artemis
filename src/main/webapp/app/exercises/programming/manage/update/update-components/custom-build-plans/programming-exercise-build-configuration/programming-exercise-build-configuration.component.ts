import { Component, OnInit, input, output, viewChild } from '@angular/core';
import { NgModel } from '@angular/forms';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';

const ALLOWED_DOCKER_FLAG_OPTIONS = ['network', 'env'];
@Component({
    selector: 'jhi-programming-exercise-build-configuration',
    templateUrl: './programming-exercise-build-configuration.component.html',
    styleUrls: ['../../../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseBuildConfigurationComponent implements OnInit {
    programmingExercise = input<ProgrammingExercise>();
    dockerImage = input.required<string>();
    dockerImageChange = output<string>();

    timeout = input<number>();
    timeoutChange = output<number>();

    envVars: string = '';
    isNetworkDisabled = false;

    isAeolus = input.required<boolean>();

    dockerImageField = viewChild<NgModel>('dockerImageField');
    timeoutField = viewChild<NgModel>('timeoutField');

    readonly NETWORK_KEY: string = 'network';
    readonly ENV_KEY: string = 'env';

    ngOnInit() {
        if (this.programmingExercise()?.buildConfig?.dockerFlags) {
            const dockerFlags = JSON.parse(this.programmingExercise()?.buildConfig?.dockerFlags || '{}') as [string, string][];
            dockerFlags.forEach(([key, value]) => {
                if (key === this.NETWORK_KEY) {
                    this.isNetworkDisabled = value === 'none';
                } else if (key === this.ENV_KEY) {
                    this.envVars = value;
                }
            });
        }
    }

    onDisableNetworkAccessChange(event: any) {
        this.updateDockerFlags(this.NETWORK_KEY, event.target.checked ? 'none' : '');
    }

    onEnvVarsChange(event: any) {
        this.updateDockerFlags(this.ENV_KEY, event);
    }

    updateDockerFlags(key: string, value: string) {
        let existingFlags = JSON.parse(this.programmingExercise()?.buildConfig?.dockerFlags || '[]') as [string, string][];
        if (!existingFlags || existingFlags.length === 0) {
            existingFlags = [] as [string, string][];
        }
        existingFlags = existingFlags.filter(([flag]) => ALLOWED_DOCKER_FLAG_OPTIONS.includes(flag) && flag !== key) || [];
        if (ALLOWED_DOCKER_FLAG_OPTIONS.includes(key) && value.trim() !== '') {
            existingFlags.push([key, value]);
        }
        this.programmingExercise()!.buildConfig!.dockerFlags = JSON.stringify(existingFlags);
    }
}
