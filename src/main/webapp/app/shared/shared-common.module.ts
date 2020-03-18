import { NgModule } from '@angular/core';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { FindLanguageFromKeyPipe } from 'app/shared/language/find-language-from-key.pipe';
import { ArtemisSharedLibsModule } from 'app/shared/shared-libs.module';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';

@NgModule({
    imports: [ArtemisSharedLibsModule],
    declarations: [FindLanguageFromKeyPipe, AlertComponent, AlertErrorComponent],
    exports: [ArtemisSharedLibsModule, FindLanguageFromKeyPipe, AlertComponent, AlertErrorComponent],
})
export class ArtemisSharedCommonModule {}
