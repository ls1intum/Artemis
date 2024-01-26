import { RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { NgModule } from '@angular/core';
import { Authority } from 'app/shared/constants/authority.constants';
import { RepositoryViewComponent } from 'app/localvc/repository-view/repository-view.component';
const routes: Routes = [
    {
        path: ':participationId',
        component: RepositoryViewComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.repository.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisProgrammingRepositoryRoutingModule {}
