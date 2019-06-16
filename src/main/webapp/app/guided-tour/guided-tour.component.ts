import { Component, Input, OnInit, OnDestroy } from '@angular/core';
import { GuidedTour, GuidedTourService, Orientation } from 'ngx-guided-tour';

@Component({
    selector: 'guided-tour',
    templateUrl: './guided-tour.component.html',
    styleUrls: ['./guided-tour.component.scss'],
})
export class GuidedTourComponent {
    title = 'ngx-guided-tour-demo';

    public dashboardTour: GuidedTour = {
        tourId: 'purchases-tour',
        useOrb: true,
        steps: [
            {
                title: 'Welcome to the Guided Tour Demo',
                selector: '.demo-title',
                content: 'Step 1',
                orientation: Orientation.Bottom,
            },
            {
                title: 'General page step',
                content: 'We have the concept of general page steps so that a you can introuce a user to a page or non specific instructions',
            },
            {
                title: 'Positioning',
                selector: '.tour-middle-content',
                content: 'Step position can be set so that steps are always in view. This step is on the left.',
                orientation: Orientation.Left,
            },
            {
                title: 'Positioning 2',
                selector: '.tour-middle-content',
                content: 'This step is on the right.',
                orientation: Orientation.Right,
            },
            {
                title: 'Scroll to content',
                selector: '.tour-scroll',
                content: 'Automatically scroll to elements so they are in view',
                orientation: Orientation.Top,
            },
        ],
    };

    constructor(private guidedTourService: GuidedTourService) {
        setTimeout(() => {
            this.guidedTourService.startTour(this.dashboardTour);
        }, 1000);
    }

    public restartTour(): void {
        this.guidedTourService.startTour(this.dashboardTour);
    }
}

class GuidedTourComponentImpl extends GuidedTourComponent {}
