import { AfterViewInit, Component, input } from '@angular/core';
import interact from 'interactjs';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { faChevronLeft, faChevronRight, faGripLinesVertical } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DiscussionFeedComponent } from './discussion-feed.component';

@Component({
    selector: 'jhi-discussion-section',
    templateUrl: './discussion-section.component.html',
    styleUrls: ['./discussion-section.component.scss'],
    imports: [FontAwesomeModule, ArtemisTranslatePipe, TranslateDirective, NgbTooltipModule, DiscussionFeedComponent],
})
export class DiscussionSectionComponent implements AfterViewInit {
    exercise = input<Exercise>();
    lecture = input<Lecture>();

    collapsed = false;

    // Icons
    faChevronRight = faChevronRight;
    faChevronLeft = faChevronLeft;
    faGripLinesVertical = faGripLinesVertical;

    /**
     * makes discussion section expandable by configuring 'interact'
     */
    ngAfterViewInit(): void {
        interact('.expanded-discussion')
            .resizable({
                edges: { left: '.draggable-left', right: false, bottom: false, top: false },
                modifiers: [
                    // Set maximum width
                    interact.modifiers!.restrictSize({
                        min: { width: 375, height: 0 },
                        max: { width: 600, height: 4000 },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function (event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function (event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function (event: any) {
                const target = event.target;
                target.style.width = event.rect.width + 'px';
            });
    }
}
