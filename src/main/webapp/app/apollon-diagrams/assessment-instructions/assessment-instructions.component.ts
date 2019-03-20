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
    @Input() collapsed = false;
    constructor() {}

    ngOnInit() {}

    ngAfterViewInit(): void {
        interact('.expanded-instructions')
            .resizable({
                edges: { left: '.draggable-left', right: false, bottom: false, top: false },
                // Set maximum width
                restrictSize: {
                    min: { width: 215 },
                    max: { width: 1000 }
                },
                inertia: true
            })
            .on('resizemove', event => {
                const target = event.target;
                target.style.width = event.rect.width + 'px';
            });
    }
}
