import { AfterViewInit, Component, Input, OnInit } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { Exercise, ExerciseType } from 'app/entities/exercise';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import interact from 'interactjs';
import { FileUploadExercise } from 'app/entities/file-upload-exercise';

@Component({
    selector: 'jhi-assessment-instructions',
    templateUrl: './assessment-instructions.component.html',
    styleUrls: ['./assessment-instructions.scss'],
})
export class AssessmentInstructionsComponent implements OnInit, AfterViewInit {
    @Input() exercise: Exercise;
    @Input() collapsed = false;

    readonly ExerciseType_MODELING = ExerciseType.MODELING;
    readonly ExerciseType_PROGRAMMING = ExerciseType.PROGRAMMING;

    formattedGradingCriteria: SafeHtml | null;
    formattedSampleSolution: SafeHtml | null;
    formattedProblemStatement: SafeHtml | null;

    constructor(private artemisMarkdown: ArtemisMarkdown) {}

    /**
     * Assigns formatted problem statement and formatted grading criteria on component initialization
     */
    ngOnInit() {
        if (this.exercise.type !== ExerciseType.PROGRAMMING) {
            this.formattedProblemStatement = this.artemisMarkdown.safeHtmlForMarkdown(this.exercise.problemStatement);
        }
        this.formattedGradingCriteria = this.artemisMarkdown.safeHtmlForMarkdown(this.exercise.gradingInstructions);
        if (this.exercise.type === ExerciseType.FILE_UPLOAD || this.exercise.type === ExerciseType.TEXT) {
            this.formattedSampleSolution = this.artemisMarkdown.safeHtmlForMarkdown((this.exercise as FileUploadExercise).sampleSolution);
        }
    }

    /**
     * Configures interact to make instructions expandable
     */
    ngAfterViewInit(): void {
        interact('.expanded-instructions')
            .resizable({
                edges: { left: '.draggable-left', right: false, bottom: false, top: false },
                modifiers: [
                    // Set maximum width
                    interact.modifiers!.restrictSize({
                        min: { width: 215, height: 0 },
                        max: { width: 1000, height: 2000 },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function(event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function(event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function(event: any) {
                const target = event.target;
                target.style.width = event.rect.width + 'px';
            });
    }
}
