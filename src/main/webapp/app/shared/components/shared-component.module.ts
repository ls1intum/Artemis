import { NgModule } from '@angular/core';
import { ButtonComponent, HelpIconComponent } from './';
import { ArtemisSharedModule } from 'app/shared';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ButtonComponent, HelpIconComponent],
    exports: [ButtonComponent, HelpIconComponent],
})
export class ArtemisSharedComponentModule {}
