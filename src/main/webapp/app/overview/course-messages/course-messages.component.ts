import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { faChevronRight, faExclamationTriangle, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ChatSession } from 'app/entities/metis/chat.session/chat-session.model';

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
    selectedChatSession: ChatSession;

    readonly FeatureToggle = FeatureToggle;

    // Icons
    faQuestionCircle = faQuestionCircle;
    faExclamationTriangle = faExclamationTriangle;
    faChevronRight = faChevronRight;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {}

    selectChatSession(chatSession: ChatSession) {
        this.selectedChatSession = chatSession;
    }
    /**
     * Resets the filter applied by chart interaction
     */
    resetFilter(): void {}
}
