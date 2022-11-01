import { Component, OnInit } from '@angular/core';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';
import { Post } from 'app/entities/metis/post.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ActivatedRoute, Params } from '@angular/router';
import { combineLatest } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { finalize } from 'rxjs/operators';

@Component({
    selector: 'jhi-course-messages',
    templateUrl: './course-messages.component.html',
    providers: [MetisService],
})
export class CourseMessagesComponent implements OnInit {
    isLoading = false;
    course: Course;
    selectedConversation: Conversation;
    postInThread: Post;
    showPostThread = false;

    constructor(protected courseManagementService: CourseManagementService, private activatedRoute: ActivatedRoute) {}

    selectConversation(conversation: Conversation) {
        this.selectedConversation = conversation;
    }

    setPostInThread(post?: Post) {
        this.showPostThread = false;

        if (!!post) {
            this.postInThread = post;
            this.showPostThread = true;
        }
    }

    ngOnInit(): void {
        this.isLoading = true;
        combineLatest({
            params: this.activatedRoute.parent!.parent!.params,
            queryParams: this.activatedRoute.parent!.parent!.queryParams,
        }).subscribe((routeParams: { params: Params; queryParams: Params }) => {
            const { params } = routeParams;
            const courseId = params.courseId;
            this.courseManagementService
                .findOneForDashboard(courseId)
                .pipe(
                    finalize(() => {
                        this.isLoading = false;
                    }),
                )
                .subscribe((res: HttpResponse<Course>) => {
                    if (res.body !== undefined) {
                        this.course = res.body!;
                    }
                });
        });
    }
}
