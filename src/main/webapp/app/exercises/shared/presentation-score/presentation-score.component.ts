import { Component, DoCheck, Input, OnDestroy } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { Exercise } from 'app/entities/exercise.model';
import { Authority } from 'app/shared/constants/authority.constants';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { Subscription } from 'rxjs';
import { GradeStepsDTO } from 'app/entities/grade-step.model';

@Component({
    selector: 'jhi-presentation-score-checkbox',
    template: `
        <ng-container *jhiHasAnyAuthority="[Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR]">
            @if (this.showPresentationScoreCheckbox()) {
                <div class="form-group">
                    <div class="form-check custom-control custom-checkbox">
                        <input
                            type="checkbox"
                            class="form-check-input custom-control-input"
                            id="field_presentationScoreEnabled"
                            name="presentationScoreEnabled"
                            [ngModel]="exercise.presentationScoreEnabled"
                            (ngModelChange)="exercise.presentationScoreEnabled = !exercise.presentationScoreEnabled"
                        />
                        <label class="form-check-label custom-control-label" for="field_presentationScoreEnabled" jhiTranslate="artemisApp.exercise.presentationScoreEnabled.title"
                            >Presentation Score Enabled</label
                        >
                        <fa-icon
                            [icon]="faQuestionCircle"
                            class="text-secondary"
                            ngbTooltip="{{ 'artemisApp.exercise.presentationScoreEnabled.description' | artemisTranslate }}"
                        />
                    </div>
                </div>
            }
        </ng-container>
    `,
})
export class PresentationScoreComponent implements DoCheck, OnDestroy {
    @Input() exercise: Exercise;

    Authority = Authority;
    // Icons
    faQuestionCircle = faQuestionCircle;

    private gradeStepsDTO?: GradeStepsDTO;
    private gradeStepsDTOSub?: Subscription;

    constructor(private gradingSystemService: GradingSystemService) {}

    ngDoCheck(): void {
        if (!this.gradeStepsDTOSub && this.exercise.course?.id) {
            this.gradeStepsDTOSub = this.gradingSystemService.findGradeStepsForCourse(this.exercise.course.id).subscribe((gradeStepsDTO) => {
                if (gradeStepsDTO.body) {
                    this.gradeStepsDTO = gradeStepsDTO.body;
                }
            });
        }
    }

    ngOnDestroy(): void {
        if (this.gradeStepsDTOSub) {
            this.gradeStepsDTOSub.unsubscribe();
        }
    }

    showPresentationScoreCheckbox(): boolean {
        return this.isBasicPresentation() || this.isGradedPresentation();
    }

    private isBasicPresentation(): boolean {
        return !!this.exercise.course?.presentationScore;
    }

    private isGradedPresentation(): boolean {
        return !!(this.exercise.course && (this.gradeStepsDTO?.presentationsNumber ?? 0) > 0);
    }
}
