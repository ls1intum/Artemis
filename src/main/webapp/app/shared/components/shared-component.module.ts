import { NgModule } from '@angular/core';
import { ButtonComponent, TooltipComponent } from './';
import { ArtemisSharedModule } from 'app/shared';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ButtonComponent, TooltipComponent],
    exports: [ButtonComponent, TooltipComponent],
})
export class ArtemisSharedComponentModule {}
