import { AfterViewInit, Component, ViewChild } from '@angular/core';
import interact from 'interactjs';
import { faChevronLeft, faChevronRight, faGripLinesVertical, faNetworkWired } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/entities/course.model';
import { LearningPathGraphComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-graph.component';

@Component({
    selector: 'jhi-learning-path-sidebar',
    styleUrls: ['./learning-path-graph-sidebar.component.scss'],
    templateUrl: './learning-path-graph-sidebar.component.html',
})
export class LearningPathGraphSidebarComponent implements AfterViewInit {
    course?: Course;

    collapsed: boolean;
    // Icons
    faChevronLeft = faChevronLeft;
    faChevronRight = faChevronRight;
    faGripLinesVertical = faGripLinesVertical;
    faNetworkWired = faNetworkWired;

    @ViewChild(`learningPathGraphComponent`, { static: false })
    learningPathGraphComponent: LearningPathGraphComponent;

    constructor() {}

    ngAfterViewInit(): void {
        // allows the conversation sidebar to be resized towards the right-hand side
        interact('.expanded-graph')
            .resizable({
                edges: { left: false, right: '.draggable-right', bottom: false, top: false },
                modifiers: [
                    // Set maximum width of the conversation sidebar
                    interact.modifiers!.restrictSize({
                        min: { width: 230, height: 0 },
                        max: { width: 500, height: 4000 },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', (event: any) => {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', (event: any) => {
                event.target.classList.remove('card-resizable');
                this.learningPathGraphComponent.onResize();
            })
            .on('resizemove', (event: any) => {
                const target = event.target;
                target.style.width = event.rect.width + 'px';
            });
    }
}
