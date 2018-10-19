import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { ApollonDiagram } from 'app/shared/model/apollon-diagram.model';
import { ApollonDiagramService } from './apollon-diagram.service';
import { ApollonDiagramComponent } from './apollon-diagram.component';
import { ApollonDiagramDetailComponent } from './apollon-diagram-detail.component';
import { ApollonDiagramUpdateComponent } from './apollon-diagram-update.component';
import { ApollonDiagramDeletePopupComponent } from './apollon-diagram-delete-dialog.component';
import { IApollonDiagram } from 'app/shared/model/apollon-diagram.model';

@Injectable({ providedIn: 'root' })
export class ApollonDiagramResolve implements Resolve<IApollonDiagram> {
    constructor(private service: ApollonDiagramService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((apollonDiagram: HttpResponse<ApollonDiagram>) => apollonDiagram.body));
        }
        return of(new ApollonDiagram());
    }
}

export const apollonDiagramRoute: Routes = [
    {
        path: 'apollon-diagram',
        component: ApollonDiagramComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.apollonDiagram.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'apollon-diagram/:id/view',
        component: ApollonDiagramDetailComponent,
        resolve: {
            apollonDiagram: ApollonDiagramResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.apollonDiagram.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'apollon-diagram/new',
        component: ApollonDiagramUpdateComponent,
        resolve: {
            apollonDiagram: ApollonDiagramResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.apollonDiagram.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'apollon-diagram/:id/edit',
        component: ApollonDiagramUpdateComponent,
        resolve: {
            apollonDiagram: ApollonDiagramResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.apollonDiagram.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const apollonDiagramPopupRoute: Routes = [
    {
        path: 'apollon-diagram/:id/delete',
        component: ApollonDiagramDeletePopupComponent,
        resolve: {
            apollonDiagram: ApollonDiagramResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.apollonDiagram.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
