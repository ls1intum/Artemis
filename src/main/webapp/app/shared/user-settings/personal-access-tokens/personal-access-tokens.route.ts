import { Routes } from '@angular/router';
import { PersonalAccessTokensComponent } from 'app/shared/user-settings/personal-access-tokens/personal-access-tokens.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';

export const personalAccessTokensRoute: Routes = [
    {
        path: '',
        component: PersonalAccessTokensComponent,
        canActivate: [UserRouteAccessService],
        data: {
            authorities: [Authority.USER],
        },
    },
];
