import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse, HttpHeaders } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiAlertService, JhiEventManager, JhiParseLinks } from 'ng-jhipster';

import { Exercise } from './exercise.model';
import { ExerciseService } from './exercise.service';
import { ITEMS_PER_PAGE, Principal } from '../../shared';

@Component({
    selector: 'jhi-exercise',
    templateUrl: './exercise.component.html'
})
export class ExerciseComponent implements OnInit, OnDestroy {

    exercises: Exercise[];
    eventSubscriber: Subscription;
    itemsPerPage: number;
    links: any;
    page: number;
    predicate: string;
    reverse: boolean;
    totalItems: number;

    constructor(
        private exerciseService: ExerciseService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private parseLinks: JhiParseLinks,
        private principal: Principal
    ) {
        this.exercises = [];
        this.itemsPerPage = ITEMS_PER_PAGE;
        this.page = 0;
        this.links = {
            last: 0
        };
        this.predicate = 'id';
        this.reverse = true;
    }

    loadAll() {
        this.exerciseService.query({
            page: this.page,
            size: this.itemsPerPage,
            sort: this.sort()
        }).subscribe(
            (res: HttpResponse<Exercise[]>) => this.onSuccess(res.body, res.headers),
            (res: HttpErrorResponse) => this.onError(res)
        );
    }

    reset() {
        this.page = 0;
        this.exercises = [];
        this.loadAll();
    }

    loadPage(page: number) {
        this.page = page;
        this.loadAll();
    }

    ngOnInit() {
        this.loadAll();
        this.registerChangeInExercises();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: Exercise) {
        return item.id;
    }
    registerChangeInExercises() {
        this.eventSubscriber = this.eventManager.subscribe('exerciseListModification', (response: any) => this.reset());
    }

    sort() {
        const result = [this.predicate + ',' + (this.reverse ? 'asc' : 'desc')];
        if (this.predicate !== 'id') {
            result.push('id');
        }
        return result;
    }

    private onSuccess(exercises: Exercise[], headers: HttpHeaders) {
        this.links = this.parseLinks.parse(headers.get('link'));
        this.totalItems = Number(headers.get('X-Total-Count'));
        for (let i = 0; i < exercises.length; i++) {
            this.exercises.push(exercises[i]);
        }
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message, null, null);
    }
}
