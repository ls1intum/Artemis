import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { faFile, faFileExport, faPencilAlt, faPuzzlePiece } from '@fortawesome/free-solid-svg-icons';
import { Lecture } from 'app/entities/lecture.model';
import { DetailOverviewSection, DetailType } from 'app/detail-overview-list/detail-overview-list.component';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { LectureService } from 'app/lecture/lecture.service';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
@Component({
    selector: 'jhi-lecture-detail',
    templateUrl: './lecture-detail.component.html',
})
export class LectureDetailComponent implements OnInit {
    lecture: Lecture;
    lectureIngestionEnabled = false;

    // Icons
    faPencilAlt = faPencilAlt;
    faFile = faFile;
    faPuzzlePiece = faPuzzlePiece;
    faFileExport = faFileExport;

    detailSections: DetailOverviewSection[];

    constructor(
        private activatedRoute: ActivatedRoute,
        private artemisMarkdown: ArtemisMarkdownService,
        protected lectureService: LectureService,
        private irisSettingsService: IrisSettingsService,
    ) {}

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit() {
        this.activatedRoute.data.subscribe(({ lecture }) => {
            this.lecture = lecture;
            this.getLectureDetailSections();
            if (this.lecture.course?.id) {
                this.irisSettingsService.getCombinedCourseSettings(this.lecture.course?.id).subscribe((settings) => {
                    if (settings) {
                        this.lectureIngestionEnabled = settings.irisLectureIngestionSettings?.enabled || false;
                    }
                });
            }
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
                        {
                            type: DetailType.Date,
                            title: 'artemisApp.lecture.visibleDate',
                            data: { date: lecture.visibleDate },
                        },
                        { type: DetailType.Date, title: 'artemisApp.lecture.startDate', data: { date: lecture.startDate } },
                        { type: DetailType.Date, title: 'artemisApp.lecture.endDate', data: { date: lecture.endDate } },
                    ],
                },
            ];
        }
    }
    /**
     * Trigger the Ingeston of this Lecture in Iris.
     */
    ingestLectureInPyris() {
        this.lectureService.ingestLecturesInPyris(this.lecture.course!.id!, this.lecture.id);
    }
}
