import { AfterViewInit, Component, Input, OnInit } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { Exercise } from 'app/entities/exercise';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import interact from 'interactjs';

@Component({
    selector: 'jhi-assessment-instructions',
    templateUrl: './assessment-instructions.component.html',
    styleUrls: ['./assessment-instructions.scss'],
})
export class AssessmentInstructionsComponent implements OnInit, AfterViewInit {
    @Input() exercise: Exercise;
    @Input() collapsed = false;

    formattedProblemStatement: SafeHtml | null;
    formattedGradingCriteria: SafeHtml | null;

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
