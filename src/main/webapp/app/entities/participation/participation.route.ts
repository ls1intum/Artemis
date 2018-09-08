import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { Participation } from 'app/shared/model/participation.model';
import { ParticipationService } from './participation.service';
import { ParticipationComponent } from './participation.component';
import { ParticipationDetailComponent } from './participation-detail.component';
import { ParticipationUpdateComponent } from './participation-update.component';
import { ParticipationDeletePopupComponent } from './participation-delete-dialog.component';
import { IParticipation } from 'app/shared/model/participation.model';

@Injectable({ providedIn: 'root' })
export class ParticipationResolve implements Resolve<IParticipation> {
    constructor(private service: ParticipationService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((participation: HttpResponse<Participation>) => participation.body));
        }
        return of(new Participation());
    }
}

export const participationRoute: Routes = [
    {
        path: 'participation',
        component: ParticipationComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.participation.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'participation/:id/view',
        component: ParticipationDetailComponent,
        resolve: {
            participation: ParticipationResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.participation.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'participation/new',
        component: ParticipationUpdateComponent,
        resolve: {
            participation: ParticipationResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.participation.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'participation/:id/edit',
        component: ParticipationUpdateComponent,
        resolve: {
            participation: ParticipationResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.participation.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const participationPopupRoute: Routes = [
    {
        path: 'participation/:id/delete',
        component: ParticipationDeletePopupComponent,
        resolve: {
            participation: ParticipationResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.participation.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
