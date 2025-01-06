import { NgModule } from '@angular/core';

import { TranslateModule } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AboutUsComponent } from 'app/core/about-us/about-us.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [
        RouterModule.forChild([
            {
                path: '',
                loadComponent: () => import('app/core/about-us/about-us.component').then((m) => m.AboutUsComponent),
                data: {
                    authorities: [],
                    pageTitle: 'overview.aboutUs',
                },
            },
        ]),
        TranslateModule,
        CommonModule,
        ArtemisSharedModule,
        AboutUsComponent,
    ],
})
export class ArtemisAboutUsModule {}
