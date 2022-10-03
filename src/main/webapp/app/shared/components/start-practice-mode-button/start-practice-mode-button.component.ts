import { Component, Input, OnInit } from '@angular/core';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { finalize } from 'rxjs/operators';
import { faRedo } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { AlertService } from 'app/core/util/alert.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';

@Component({
    selector: 'jhi-start-practice-mode-button',
    templateUrl: './start-practice-mode-button.component.html',
})
export class StartPracticeModeButtonComponent implements OnInit {
    readonly FeatureToggle = FeatureToggle;

    @Input()
    smallButtons: boolean;
    @Input()
    exercise: Exercise;

    startingPracticeMode = false;
    gradedStudentParticipation?: StudentParticipation;

    // Icons
    faRedo = faRedo;

    constructor(private courseExerciseService: CourseExerciseService, private alertService: AlertService, private participationService: ParticipationService) {}

    ngOnInit() {
        this.gradedStudentParticipation = this.participationService.getSpecificStudentParticipation(this.exercise.studentParticipations ?? [], false);
    }

    startPractice(useGradedParticipation: boolean): void {
        this.startingPracticeMode = true;
        this.courseExerciseService
            .startPractice(this.exercise.id!, useGradedParticipation)
            .pipe(finalize(() => (this.startingPracticeMode = false)))
            .subscribe({
                next: (participation) => {
                    if (participation) {
                        if (this.exercise.studentParticipations?.some((studentParticipation) => studentParticipation.id === participation.id)) {
                            this.exercise.studentParticipations = this.exercise.studentParticipations?.map((studentParticipation) =>
                                studentParticipation.id === participation.id ? participation : studentParticipation,
                            );
                        } else {
                            this.exercise.studentParticipations = [...(this.exercise.studentParticipations ?? []), participation];
                        }
                    }
                    if (this.exercise.type === ExerciseType.PROGRAMMING) {
                        if ((this.exercise as ProgrammingExercise).allowOfflineIde) {
                            this.alertService.success('artemisApp.exercise.personalRepositoryClone');
                        } else {
                            this.alertService.success('artemisApp.exercise.personalRepositoryOnline');
                        }
                    }
                },
                error: () => {
                    this.alertService.warning('artemisApp.exercise.startError');
                },
            });
    }
}
