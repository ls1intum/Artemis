import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';

import { ThemeSwitchComponent } from 'app/core/theme/theme-switch.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    declarations: [ThemeSwitchComponent],
    imports: [TranslateModule, CommonModule, ArtemisSharedModule],
    exports: [ThemeSwitchComponent],
})
export class ThemeModule {}
