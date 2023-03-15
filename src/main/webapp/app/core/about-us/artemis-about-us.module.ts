import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

import { AboutUsComponent } from 'app/core/about-us/about-us.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    declarations: [AboutUsComponent],
    imports: [
        RouterModule.forChild([
            {
                path: '',
                component: AboutUsComponent,
                data: {
                    authorities: [],
                    pageTitle: 'overview.aboutUs',
                },
            },
        ]),
        TranslateModule,
        CommonModule,
        ArtemisSharedModule,
    ],
})
export class ArtemisAboutUsModule {}
