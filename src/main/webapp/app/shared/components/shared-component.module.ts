import { NgModule } from '@angular/core';
import { ButtonComponent } from './';
import { ArtemisSharedModule } from 'app/shared';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ButtonComponent],
    exports: [ButtonComponent],
})
export class ArtemisSharedComponentModule {}
