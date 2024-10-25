import { Component, OnInit, inject, input, output, viewChild } from '@angular/core';
import { NgModel } from '@angular/forms';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';

@Component({
    selector: 'jhi-programming-exercise-build-configuration',
    templateUrl: './programming-exercise-build-configuration.component.html',
    styleUrls: ['../../../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseBuildConfigurationComponent implements OnInit {
    private profileService = inject(ProfileService);

    dockerImage = input.required<string>();
    dockerImageChange = output<string>();

    timeout = input<number>();
    timeoutChange = output<number>();

    isAeolus = input.required<boolean>();

    dockerImageField = viewChild<NgModel>('dockerImageField');

    timeoutField = viewChild<NgModel>('timeoutField');

    timeoutMinValue?: number;
    timeoutMaxValue?: number;
    timeoutDefaultValue?: number;

    ngOnInit() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.timeoutMinValue = profileInfo.buildTimeoutMin ?? 10;
                this.timeoutMaxValue = profileInfo.buildTimeoutMax ?? 240;
                this.timeoutDefaultValue = profileInfo.buildTimeoutDefault ?? 120;

                if (!this.timeout) {
                    this.timeoutChange.emit(this.timeoutDefaultValue);
                }
            }
        });
    }
}
