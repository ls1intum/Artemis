import { HttpResponse } from '@angular/common/http';
import { NgClass } from '@angular/common';
import { Component, Input, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ExerciseDetailsType, ExerciseService } from 'app/exercise/services/exercise.service';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

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

    @Input() public exercise?: Exercise;
    @Input() participation?: StudentParticipation;
    isStandalone: boolean;

    ngOnInit() {
        this.route.params.subscribe((params) => {
            const exerciseId = parseInt(params['exerciseId'], 10);
            let participationId: number | undefined = undefined;
            if (params['participationId']) {
                participationId = parseInt(params['participationId'], 10);
            }

            if (!this.exercise) {
                this.exerciseService.getExerciseDetails(exerciseId).subscribe((exerciseResponse: HttpResponse<ExerciseDetailsType>) => {
                    this.exercise = exerciseResponse.body!.exercise;
                });
            }
            if (!this.participation && participationId) {
                this.participationService.find(participationId).subscribe((participationResponse) => {
                    this.participation = participationResponse.body!;
                });
            }
        });
        // Check whether problem statement is displayed standalone (mobile apps)
        this.route.url.subscribe((segments) => {
            this.isStandalone = segments.some(segment => segment.path == "problem-statement");
        });
    }

    get isProgrammingExercise(): boolean {
        return this.exercise?.type === ExerciseType.PROGRAMMING;
    }
}
