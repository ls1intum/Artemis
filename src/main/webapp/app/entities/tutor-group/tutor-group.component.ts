import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { JhiEventManager } from 'ng-jhipster';
import { AccountService } from 'app/core/auth/account.service';
import { TutorGroupService } from './tutor-group.service';
import { TutorGroup } from 'app/entities/tutor-group/tutor-group.model';
import { AlertService } from 'app/core/alert/alert.service';

@Component({
    selector: 'jhi-tutor-group',
    templateUrl: './tutor-group.component.html',
})
export class TutorGroupComponent implements OnInit, OnDestroy {
    tutorGroups: TutorGroup[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        protected tutorGroupService: TutorGroupService,
        protected jhiAlertService: AlertService,
        protected eventManager: JhiEventManager,
        protected accountService: AccountService,
    ) {}

    loadAll() {
        this.tutorGroupService
            .query()
            .pipe(
                filter((res: HttpResponse<TutorGroup[]>) => res.ok),
                map((res: HttpResponse<TutorGroup[]>) => res.body),
            )
            .subscribe(
                (res: TutorGroup[]) => {
                    this.tutorGroups = res;
                },
                (res: HttpErrorResponse) => this.onError(res.message),
            );
    }

    ngOnInit() {
        this.loadAll();
        this.accountService.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInTutorGroups();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: TutorGroup) {
        return item.id;
    }

    registerChangeInTutorGroups() {
        this.eventSubscriber = this.eventManager.subscribe('tutorGroupListModification', () => this.loadAll());
    }

    protected onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, undefined);
    }
}
