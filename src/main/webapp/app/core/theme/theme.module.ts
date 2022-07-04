import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TranslateModule } from '@ngx-translate/core';
import { ThemeSwitchComponent } from 'app/core/theme/theme-switch.component';

@NgModule({
    declarations: [ThemeSwitchComponent],
    imports: [TranslateModule, CommonModule, ArtemisSharedModule],
    exports: [ThemeSwitchComponent],
})
export class ThemeModule {}
