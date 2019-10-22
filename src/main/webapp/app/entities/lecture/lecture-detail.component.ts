import { Component, OnInit, OnDestroy } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { Lecture, LectureService } from 'app/entities/lecture';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';

@Component({
    selector: 'jhi-lecture-detail',
    templateUrl: './lecture-detail.component.html',
})
export class LectureDetailComponent implements OnInit, OnDestroy {
    lecture: Lecture;

    formattedDescription: SafeHtml | null;

    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        protected activatedRoute: ActivatedRoute,
        private eventManager: JhiEventManager,
        private lectureService: LectureService,
        private artemisMarkdown: ArtemisMarkdown,
    ) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ lecture }) => {
            this.lecture = lecture;
        });
        this.subscription = this.activatedRoute.params.subscribe(params => {
            this.load(params['id']);
        });
        this.registerChangeInLecture();
    }

    load(id: number) {
        this.lectureService.find(id).subscribe((lectureResponse: HttpResponse<Lecture>) => {
            this.lecture = lectureResponse.body!;

            this.formattedDescription = this.artemisMarkdown.htmlForMarkdown(this.lecture.description);
        });
    }

    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInLecture() {
        this.eventSubscriber = this.eventManager.subscribe('lectureListModification', () => this.load(this.lecture.id));
    }
}
