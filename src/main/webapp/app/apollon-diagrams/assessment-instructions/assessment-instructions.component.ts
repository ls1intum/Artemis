import { Component, OnInit, Input, AfterViewInit } from '@angular/core';
import { Exercise } from 'app/entities/exercise';
import interact from 'interactjs';
@Component({
    selector: 'jhi-assessment-instructions',
    templateUrl: './assessment-instructions.component.html',
    styleUrls: ['./assessment-instructions.component.scss']
})
export class AssessmentInstructionsComponent implements OnInit, AfterViewInit {
    @Input() exercise: Exercise;
    constructor() {}

    ngOnInit() {}

    ngAfterViewInit(): void {
        interact('.resizable-instructions')
            .resizable({
                // Enable resize from left edge; triggered by class rg-left
                edges: { left: '.draggable-left', right: false, bottom: false, top: false },
                // Set maximum width
                restrictSize: {
                    min: { width: 215 },
                    max: { width: 500 }
                },
                inertia: true
            })
            .on('resizemove', event => {
                const target = event.target;
                // Update element width
                target.style.width = event.rect.width + 'px';
            });
    }
}
