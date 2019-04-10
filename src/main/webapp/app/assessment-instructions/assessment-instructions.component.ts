import { AfterViewInit, Component, Input, OnInit } from '@angular/core';
import * as interact from 'interactjs';
import { Exercise } from 'app/entities/exercise';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';

@Component({
    selector: 'jhi-assessment-instructions',
    templateUrl: './assessment-instructions.component.html',
    styleUrls: ['./assessment-instructions.scss'],
})
export class AssessmentInstructionsComponent implements OnInit, AfterViewInit {
    @Input() exercise: Exercise;
    @Input() collapsed = false;

    formattedProblemStatement: string;
    formattedGradingCriteria: string;

    constructor(private artemisMarkdown: ArtemisMarkdown) {}

    ngOnInit() {
        this.formattedProblemStatement = this.artemisMarkdown.htmlForMarkdown(this.exercise.problemStatement);
        this.formattedGradingCriteria = this.artemisMarkdown.htmlForMarkdown(this.exercise.gradingInstructions);
    }

    ngAfterViewInit(): void {
        interact('.expanded-instructions')
            .resizable({
                edges: { left: '.draggable-left', right: false, bottom: false, top: false },
                // Set maximum width
                restrictSize: {
                    min: { width: 215 },
                    max: { width: 1000 },
                },
                inertia: true,
            })
            .on('resizemove', event => {
                const target = event.target;
                target.style.width = event.rect.width + 'px';
            });
    }
}
