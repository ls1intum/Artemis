import { NgModule } from '@angular/core';
import { AboutUsComponent } from 'app/core/about-us/about-us.component';
import { TranslateModule } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    declarations: [AboutUsComponent],
    imports: [
        RouterModule.forChild([
            {
                path: '',
                component: AboutUsComponent,
            },
        ]),
        TranslateModule,
        CommonModule,
        ArtemisSharedModule,
    ],
})
export class ArtemisAboutUsModule {}
