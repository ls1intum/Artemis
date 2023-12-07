import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { faFile, faPencilAlt, faPuzzlePiece } from '@fortawesome/free-solid-svg-icons';
import { Lecture } from 'app/entities/lecture.model';
import { DetailOverviewSection, DetailType } from 'app/detail-overview-list/detail-overview-list.component';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';

@Component({
    selector: 'jhi-lecture-detail',
    templateUrl: './lecture-detail.component.html',
})
export class LectureDetailComponent implements OnInit {
    lecture: Lecture;

    // Icons
    faPencilAlt = faPencilAlt;
    faFile = faFile;
    faPuzzlePiece = faPuzzlePiece;

    detailSections: DetailOverviewSection[];

    constructor(
        private activatedRoute: ActivatedRoute,
        private artemisMarkdown: ArtemisMarkdownService,
    ) {}

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit() {
        this.activatedRoute.data.subscribe(({ lecture }) => {
            this.lecture = lecture;
        });
    }

    getLectureDetailSections() {
        const lecture = this.lecture;
        const descriptionMarkdown = this.artemisMarkdown.safeHtmlForMarkdown(lecture.description);
        this.detailSections = [
            {
                headline: 'artemisApp.lecture.detail.sections.general',
                details: [
                    lecture.course && {
                        type: DetailType.Link,
                        title: 'artemisApp.lecture.course',
                        data: { routerLink: ['/course-management', lecture.course.id], text: lecture.course.title },
                    },
                    { type: DetailType.Text, title: 'artemisApp.lecture.title', data: { text: lecture.title } },
                    { type: DetailType.Markdown, title: 'artemisApp.lecture.description', data: { innerHtml: descriptionMarkdown } },
                    { type: DetailType.Date, title: 'artemisApp.lecture.visibleDate', data: { date: lecture.visibleDate } },
                    { type: DetailType.Date, title: 'artemisApp.lecture.startDate', data: { date: lecture.startDate } },
                    { type: DetailType.Date, title: 'artemisApp.lecture.endDate', data: { date: lecture.endDate } },
                ].filter(Boolean),
            } as DetailOverviewSection,
        ];
    }
}
