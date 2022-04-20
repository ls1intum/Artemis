import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TranslateModule } from '@ngx-translate/core';

@NgModule({
    declarations: [],
    imports: [TranslateModule, CommonModule, ArtemisSharedModule],
})
export class ThemeModule {}
