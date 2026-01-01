import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { faFile, faPencilAlt, faPuzzlePiece } from '@fortawesome/free-solid-svg-icons';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { DetailOverviewSection, DetailType } from 'app/shared/detail-overview-list/detail-overview-list.component';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DetailOverviewListComponent } from 'app/shared/detail-overview-list/detail-overview-list.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-lecture-detail',
    templateUrl: './lecture-detail.component.html',
    imports: [TranslateDirective, DetailOverviewListComponent, RouterLink, FaIconComponent],
})
export class LectureDetailComponent implements OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private artemisMarkdown = inject(ArtemisMarkdownService);

    lecture: Lecture;

    // Icons
    faPencilAlt = faPencilAlt;
    faFile = faFile;
    faPuzzlePiece = faPuzzlePiece;

    detailSections: DetailOverviewSection[];

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit() {
        this.activatedRoute.data.subscribe(({ lecture }) => {
            this.lecture = lecture;
            this.getLectureDetailSections();
        });
    }

    getLectureDetailSections() {
        const lecture = this.lecture;
        const descriptionMarkdown = this.artemisMarkdown.safeHtmlForMarkdown(lecture.description);
        if (lecture.course) {
            this.detailSections = [
                {
                    headline: 'artemisApp.lecture.detail.sections.general',
                    details: [
                        {
                            type: DetailType.Link,
                            title: 'artemisApp.lecture.course',
                            data: { routerLink: ['/course-management', lecture.course.id], text: lecture.course.title },
                        },
                        { type: DetailType.Text, title: 'artemisApp.lecture.title', data: { text: lecture.title } },
                        {
                            type: DetailType.Markdown,
                            title: 'artemisApp.lecture.description',
                            data: { innerHtml: descriptionMarkdown },
                        },
                        /* The visibleDate property of the Lecture entity is deprecated. We're keeping the related logic temporarily to monitor for user feedback before full removal */
                        /* TODO: #11479 - remove the commented out code OR comment back in */
                        //{
                        //    type: DetailType.Date,
                        //    title: 'artemisApp.lecture.visibleDate',
                        //    data: { date: lecture.visibleDate },
                        //},
                        { type: DetailType.Date, title: 'artemisApp.lecture.startDate', data: { date: lecture.startDate } },
                        { type: DetailType.Date, title: 'artemisApp.lecture.endDate', data: { date: lecture.endDate } },
                    ],
                },
            ];
        }
    }
}
