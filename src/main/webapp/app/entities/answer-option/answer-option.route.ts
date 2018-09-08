import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { AnswerOption } from 'app/shared/model/answer-option.model';
import { AnswerOptionService } from './answer-option.service';
import { AnswerOptionComponent } from './answer-option.component';
import { AnswerOptionDetailComponent } from './answer-option-detail.component';
import { AnswerOptionUpdateComponent } from './answer-option-update.component';
import { AnswerOptionDeletePopupComponent } from './answer-option-delete-dialog.component';
import { IAnswerOption } from 'app/shared/model/answer-option.model';

@Injectable({ providedIn: 'root' })
export class AnswerOptionResolve implements Resolve<IAnswerOption> {
    constructor(private service: AnswerOptionService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((answerOption: HttpResponse<AnswerOption>) => answerOption.body));
        }
        return of(new AnswerOption());
    }
}

export const answerOptionRoute: Routes = [
    {
        path: 'answer-option',
        component: AnswerOptionComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.answerOption.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'answer-option/:id/view',
        component: AnswerOptionDetailComponent,
        resolve: {
            answerOption: AnswerOptionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.answerOption.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'answer-option/new',
        component: AnswerOptionUpdateComponent,
        resolve: {
            answerOption: AnswerOptionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.answerOption.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'answer-option/:id/edit',
        component: AnswerOptionUpdateComponent,
        resolve: {
            answerOption: AnswerOptionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.answerOption.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const answerOptionPopupRoute: Routes = [
    {
        path: 'answer-option/:id/delete',
        component: AnswerOptionDeletePopupComponent,
        resolve: {
            answerOption: AnswerOptionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.answerOption.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
