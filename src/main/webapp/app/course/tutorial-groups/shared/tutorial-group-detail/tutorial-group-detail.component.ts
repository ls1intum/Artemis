import { Component, ContentChild, Input, OnChanges, SimpleChanges, TemplateRef } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { Language } from 'app/entities/course.model';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { getDayTranslationKey } from '../weekdays';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/entities/tutorial-group/tutorial-group-session.model';
import dayjs from 'dayjs/esm';
import { SortService } from 'app/shared/service/sort.service';

@Component({
    selector: 'jhi-tutorial-group-detail',
    templateUrl: './tutorial-group-detail.component.html',
})
export class TutorialGroupDetailComponent implements OnChanges {
    @ContentChild(TemplateRef) header: TemplateRef<any>;

    @Input()
    tutorialGroup: TutorialGroup;

    @Input()
    courseClickHandler: () => void;

    @Input()
    registrationClickHandler: () => void;

    GERMAN = Language.GERMAN;
    ENGLISH = Language.ENGLISH;
    formattedAdditionalInformation?: SafeHtml;
    getDayTranslationKey = getDayTranslationKey;

    pastSessions: TutorialGroupSession[] = [];
    upcomingSessions: TutorialGroupSession[] = [];

    constructor(private artemisMarkdownService: ArtemisMarkdownService, private sortService: SortService) {}

    ngOnChanges(changes: SimpleChanges) {
        for (const propName in changes) {
            if (changes.hasOwnProperty(propName)) {
                const change = changes[propName];
                switch (propName) {
                    case 'tutorialGroup': {
                        if (change.currentValue && change.currentValue.additionalInformation) {
                            this.formattedAdditionalInformation = this.artemisMarkdownService.safeHtmlForMarkdown(this.tutorialGroup.additionalInformation);
                        }
                        if (change.currentValue && change.currentValue.tutorialGroupSessions) {
                            this.splitIntoUpcomingAndPastSessions(this.sortService.sortByProperty(change.currentValue.tutorialGroupSessions, 'start', false));
                        }
                    }
                }
            }
        }
    }

    public getCurrentDate(): dayjs.Dayjs {
        return dayjs();
    }

    private splitIntoUpcomingAndPastSessions(sessions: TutorialGroupSession[]) {
        const upcoming: TutorialGroupSession[] = [];
        const past: TutorialGroupSession[] = [];
        const now = this.getCurrentDate();

        for (const session of sessions) {
            if (session.status !== TutorialGroupSessionStatus.ACTIVE) {
                continue;
            }

            if (session.end!.isBefore(now)) {
                past.push(session);
            } else {
                upcoming.push(session);
            }
        }
        this.upcomingSessions = upcoming;
        this.pastSessions = past;
    }
}
