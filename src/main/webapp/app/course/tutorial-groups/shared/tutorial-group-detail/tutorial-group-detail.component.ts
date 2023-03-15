import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ContentChild, Input, OnChanges, SimpleChanges, TemplateRef } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { Course, Language } from 'app/entities/course.model';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { getDayTranslationKey } from '../weekdays';

@Component({
    selector: 'jhi-tutorial-group-detail',
    templateUrl: './tutorial-group-detail.component.html',
    styleUrls: ['./tutorial-group-detail.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TutorialGroupDetailComponent implements OnChanges {
    @ContentChild(TemplateRef, { static: true }) header: TemplateRef<any>;

    @Input()
    timeZone?: string = undefined;

    @Input()
    tutorialGroup: TutorialGroup;

    @Input()
    courseClickHandler: () => void;

    @Input()
    registrationClickHandler: () => void;

    @Input()
    course: Course;
    GERMAN = Language.GERMAN;
    ENGLISH = Language.ENGLISH;
    formattedAdditionalInformation?: SafeHtml;
    getDayTranslationKey = getDayTranslationKey;

    faQuestionCircle = faQuestionCircle;
    readonly Math = Math;

    sessions: TutorialGroupSession[] = [];

    constructor(private artemisMarkdownService: ArtemisMarkdownService, private changeDetectorRef: ChangeDetectorRef) {}

    ngOnChanges(changes: SimpleChanges) {
        for (const propName in changes) {
            // eslint-disable-next-line no-prototype-builtins
            if (changes.hasOwnProperty(propName) && propName === 'tutorialGroup') {
                const change = changes[propName];

                if (change.currentValue && change.currentValue.additionalInformation) {
                    this.formattedAdditionalInformation = this.artemisMarkdownService.safeHtmlForMarkdown(this.tutorialGroup.additionalInformation);
                }
                if (change.currentValue && change.currentValue.tutorialGroupSessions) {
                    this.sessions = change.currentValue.tutorialGroupSessions;
                }
                this.changeDetectorRef.detectChanges();
            }
        }
    }
}
