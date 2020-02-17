import { NgModule } from '@angular/core';
import { JhiAlertComponent } from 'app/shared/alert/alert.component';
import { FindLanguageFromKeyPipe } from 'app/shared/language/find-language-from-key.pipe';
import { ArtemisSharedLibsModule } from 'app/shared/shared-libs.module';
import { JhiAlertErrorComponent } from 'app/shared/alert/alert-error.component';

@NgModule({
    imports: [ArtemisSharedLibsModule],
    declarations: [FindLanguageFromKeyPipe, JhiAlertComponent, JhiAlertErrorComponent],
    exports: [ArtemisSharedLibsModule, FindLanguageFromKeyPipe, JhiAlertComponent, JhiAlertErrorComponent],
})
export class ArtemisSharedCommonModule {}
