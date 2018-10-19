import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';

import { ILtiUserId } from 'app/shared/model/lti-user-id.model';
import { LtiUserIdService } from './lti-user-id.service';
import { IUser, UserService } from 'app/core';

@Component({
    selector: 'jhi-lti-user-id-update',
    templateUrl: './lti-user-id-update.component.html'
})
export class LtiUserIdUpdateComponent implements OnInit {
    ltiUserId: ILtiUserId;
    isSaving: boolean;

    users: IUser[];

    constructor(
        private jhiAlertService: JhiAlertService,
        private ltiUserIdService: LtiUserIdService,
        private userService: UserService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ ltiUserId }) => {
            this.ltiUserId = ltiUserId;
        });
        this.userService.query().subscribe(
            (res: HttpResponse<IUser[]>) => {
                this.users = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.ltiUserId.id !== undefined) {
            this.subscribeToSaveResponse(this.ltiUserIdService.update(this.ltiUserId));
        } else {
            this.subscribeToSaveResponse(this.ltiUserIdService.create(this.ltiUserId));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<ILtiUserId>>) {
        result.subscribe((res: HttpResponse<ILtiUserId>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }

    trackUserById(index: number, item: IUser) {
        return item.id;
    }
}
