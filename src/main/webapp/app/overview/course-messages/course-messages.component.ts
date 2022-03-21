import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { faChevronRight, faExclamationTriangle, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';

@Component({
    selector: 'jhi-messages',
    styleUrls: ['./course-messages.component.scss'],
    templateUrl: './course-messages.component.html',
})
export class CourseMessagesComponent implements OnInit {
    /**
     * True, if an automated plagiarism detection is running; false otherwise.
     */
    detectionInProgress = false;

    detectionInProgressMessage = '';

    /**
     * Index of the currently selected comparison.
     */
    selectedChatSessionId: number;

    readonly FeatureToggle = FeatureToggle;

    // Icons
    faQuestionCircle = faQuestionCircle;
    faExclamationTriangle = faExclamationTriangle;
    faChevronRight = faChevronRight;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {}

    selectChatSession(id: number) {
        this.selectedChatSessionId = id;
    }
    /**
     * Resets the filter applied by chart interaction
     */
    resetFilter(): void {}
}
