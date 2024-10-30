import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TitleChannelNameComponent } from 'app/shared/form/title-channel-name/title-channel-name.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { CustomNotIncludedInValidatorDirective } from 'app/shared/validators/custom-not-included-in-validator.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@NgModule({
    imports: [FormsModule, CommonModule, ArtemisSharedComponentModule, CustomNotIncludedInValidatorDirective, TranslateDirective],
    declarations: [TitleChannelNameComponent],
    exports: [TitleChannelNameComponent],
})
export class TitleChannelNameModule {}
