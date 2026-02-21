import { HttpResponse } from '@angular/common/http';
import { NgClass } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ExerciseDetailsType, ExerciseService } from 'app/exercise/services/exercise.service';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

@Component({
    selector: 'jhi-problem-statement',
    templateUrl: './problem-statement.component.html',
    styleUrls: ['../../course-overview/course-overview.scss'],
    imports: [ProgrammingExerciseInstructionComponent, TranslateDirective, HtmlForMarkdownPipe, NgClass],
})
export class ProblemStatementComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private exerciseService = inject(ExerciseService);
    private participationService = inject(ParticipationService);
    private destroyRef = inject(DestroyRef);

    public readonly exerciseInput = input<Exercise>();
    readonly participationInput = input<StudentParticipation>();

    private readonly fetchedExercise = signal<Exercise | undefined>(undefined);
    private readonly fetchedParticipation = signal<StudentParticipation | undefined>(undefined);

    readonly exercise = computed(() => this.exerciseInput() ?? this.fetchedExercise());
    readonly participation = computed(() => this.participationInput() ?? this.fetchedParticipation());

    /** Returns the exercise as ProgrammingExercise if it's a programming exercise, undefined otherwise */
    readonly programmingExercise = computed(() => {
        const ex = this.exercise();
        return ex?.type === ExerciseType.PROGRAMMING ? (ex as ProgrammingExercise) : undefined;
    });

    isStandalone: boolean = false;

    ngOnInit() {
        this.route.params.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
            const exerciseId = parseInt(params['exerciseId'], 10);
            let participationId: number | undefined = undefined;
            if (params['participationId']) {
                participationId = parseInt(params['participationId'], 10);
            }

            if (!this.exercise()) {
                this.exerciseService.getExerciseDetails(exerciseId).subscribe((exerciseResponse: HttpResponse<ExerciseDetailsType>) => {
                    this.fetchedExercise.set(exerciseResponse.body!.exercise);
                });
            }
            if (!this.participation() && participationId) {
                this.participationService.find(participationId).subscribe((participationResponse) => {
                    this.fetchedParticipation.set(participationResponse.body!);
                });
            }
        });
        // Check whether problem statement is displayed standalone (mobile apps)
        const url = this.route.url;
        if (url) {
            url.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((segments) => {
                this.isStandalone = segments.some((segment) => segment.path == 'problem-statement');
            });
        }
    }
}
