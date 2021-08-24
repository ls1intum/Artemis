import { AfterViewInit, Component, Input, OnDestroy } from '@angular/core';
import interact from 'interactjs';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { PageType } from 'app/shared/metis/metis.util';
import { Course } from 'app/entities/course.model';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';

@Component({
    selector: 'jhi-page-discussion-section',
    templateUrl: './page-discussion-section.component.html',
    styleUrls: ['./page-discussion-section.scss'],
})
export class PageDiscussionSectionComponent implements AfterViewInit, OnDestroy {
    @Input() exercise?: Exercise;
    @Input() lecture?: Lecture;
    course?: Course;
    collapsed = false;
    readonly pageType = PageType.PAGE_SECTION;

    private paramSubscription: Subscription;

    constructor(private activatedRoute: ActivatedRoute, private courseCalculationService: CourseScoreCalculationService) {
        this.paramSubscription = this.activatedRoute.params.subscribe((params) => {
            const courseId = parseInt(params['courseId'], 10);
            this.course = this.courseCalculationService.getCourse(courseId);
        });
    }

    ngOnDestroy(): void {
        this.paramSubscription?.unsubscribe();
    }

    /**
     * makes discussion expandable by configuring 'interact'
     */
    ngAfterViewInit(): void {
        interact('.expanded-discussion')
            .resizable({
                edges: { left: '.draggable-left', right: false, bottom: false, top: false },
                modifiers: [
                    // Set maximum width
                    interact.modifiers!.restrictSize({
                        min: { width: 360, height: 0 },
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
