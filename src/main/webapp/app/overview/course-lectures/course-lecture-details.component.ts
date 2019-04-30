import { Component, OnDestroy, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { JhiWebsocketService } from 'app/core';
import { HttpResponse } from '@angular/common/http';
import { Lecture, LectureService } from 'app/entities/lecture';
import * as moment from 'moment';
import { Attachment } from 'app/entities/attachment';

@Component({
    selector: 'jhi-course-lecture-details',
    templateUrl: './course-lecture-details.component.html',
    styleUrls: ['../course-overview.scss', './course-lectures.scss'],
})
export class CourseLectureDetailsComponent implements OnInit, OnDestroy {
    private subscription: Subscription;
    public lecture: Lecture;

    constructor(
        private $location: Location,
        private jhiWebsocketService: JhiWebsocketService,
        private lectureService: LectureService,
        private route: ActivatedRoute,
        private router: Router,
    ) {
        const navigation = this.router.getCurrentNavigation();
        if (navigation.extras.state) {
            const stateLecture = navigation.extras.state.lecture as Lecture;
            if (stateLecture && stateLecture.startDate) {
                stateLecture.startDate = moment(stateLecture.startDate);
            }
            if (stateLecture && stateLecture.endDate) {
                stateLecture.endDate = moment(stateLecture.endDate);
            }
            this.lecture = stateLecture;
        }
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            if (!this.lecture) {
                this.lectureService.find(params.lectureId).subscribe((lectureResponse: HttpResponse<Lecture>) => {
                    this.lecture = lectureResponse.body!;
                });
            }
        });
    }

    ngOnDestroy() {
        if (this.subscription) {
            this.subscription.unsubscribe();
        }
    }

    backToCourse() {
        this.$location.back();
    }

    attachmentNotReleased(attachment: Attachment) {
        return attachment.releaseDate && !moment(attachment.releaseDate).isBefore(moment());
    }

    attachmentExtension(attachment: Attachment) {
        if (!attachment.link) {
            return 'N/A';
        }

        return attachment.link.split('.').pop();
    }
}
