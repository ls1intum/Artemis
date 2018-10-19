import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { IApollonDiagram } from 'app/shared/model/apollon-diagram.model';
import { ApollonDiagramService } from './apollon-diagram.service';

@Component({
    selector: 'jhi-apollon-diagram-update',
    templateUrl: './apollon-diagram-update.component.html'
})
export class ApollonDiagramUpdateComponent implements OnInit {
    apollonDiagram: IApollonDiagram;
    isSaving: boolean;

    constructor(private apollonDiagramService: ApollonDiagramService, private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ apollonDiagram }) => {
            this.apollonDiagram = apollonDiagram;
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.apollonDiagram.id !== undefined) {
            this.subscribeToSaveResponse(this.apollonDiagramService.update(this.apollonDiagram));
        } else {
            this.subscribeToSaveResponse(this.apollonDiagramService.create(this.apollonDiagram));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IApollonDiagram>>) {
        result.subscribe((res: HttpResponse<IApollonDiagram>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }
}
