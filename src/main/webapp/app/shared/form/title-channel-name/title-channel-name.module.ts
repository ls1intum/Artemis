import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TitleChannelNameComponent } from 'app/shared/form/title-channel-name/title-channel-name.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    imports: [FormsModule, CommonModule, ArtemisSharedComponentModule],
    declarations: [TitleChannelNameComponent],
    exports: [TitleChannelNameComponent],
})
export class TitleChannelNameModule {}
