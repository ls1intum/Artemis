import { NgModule } from '@angular/core';
import { AboutUsComponent } from 'app/core/about-us/about-us.component';
import { AboutUsRoutingModule } from 'app/core/about-us/about-us-routing.module';
import { TranslateModule } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';

@NgModule({
    declarations: [AboutUsComponent],
    imports: [AboutUsRoutingModule, TranslateModule, CommonModule],
})
export class ArtemisAboutUsModule {}
