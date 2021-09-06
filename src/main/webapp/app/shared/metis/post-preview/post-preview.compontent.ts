import { Component, Input, OnInit } from '@angular/core';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Post } from 'app/entities/metis/post.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { ContextInformation } from 'app/shared/metis/metis.util';
import { Router } from '@angular/router';

@Component({
    selector: 'jhi-post-preview',
    templateUrl: './post-preview.component.html',
    styleUrls: ['./post-preview.component.scss'],
})
export class PostPreviewComponent implements OnInit {
    @Input() post: Post;
    @Input() ref?: NgbModalRef;
    contextInformation: ContextInformation;

    constructor(public metisService: MetisService, private router: Router) {}

    /**
     * on initialization: updates the post tags and the context information
     */
    ngOnInit(): void {
        this.contextInformation = this.metisService.getContextInformation(this.post);
    }

    routeToPost() {
        this.ref?.dismiss();
        this.router.navigate(this.metisService.getLinkForPost(this.post), {
            queryParams: this.metisService.getQueryParamsForPost(this.post),
        });
    }

    routeToContext() {
        this.ref?.dismiss();
        this.router.navigate(this.contextInformation.routerLinkComponents!);
    }
}
