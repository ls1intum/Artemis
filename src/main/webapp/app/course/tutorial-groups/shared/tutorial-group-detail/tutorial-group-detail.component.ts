import { Component, ContentChild, Input, OnChanges, OnInit, SimpleChanges, TemplateRef } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { Language } from 'app/entities/course.model';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';

@Component({
    selector: 'jhi-tutorial-group-detail',
    templateUrl: './tutorial-group-detail.component.html',
    styleUrls: ['./tutorial-group-detail.component.scss'],
})
export class TutorialGroupDetailComponent implements OnChanges {
    @ContentChild(TemplateRef) header: TemplateRef<any>;

    @Input()
    tutorialGroup: TutorialGroup;

    GERMAN = Language.GERMAN;
    ENGLISH = Language.ENGLISH;
    formattedAdditionalInformation?: SafeHtml;

    constructor(private artemisMarkdownService: ArtemisMarkdownService) {}

    ngOnChanges(changes: SimpleChanges) {
        for (const propName in changes) {
            if (changes.hasOwnProperty(propName)) {
                const change = changes[propName];
                switch (propName) {
                    case 'tutorialGroup': {
                        if (change.currentValue && change.currentValue.additionalInformation) {
                            this.formattedAdditionalInformation = this.artemisMarkdownService.safeHtmlForMarkdown(this.tutorialGroup.additionalInformation);
                        }
                    }
                }
            }
        }
    }
}
