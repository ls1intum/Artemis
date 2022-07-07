import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { ClipboardModule } from '@angular/cdk/clipboard';
import { PersonalAccessTokensComponent } from 'app/shared/user-settings/personal-access-tokens/personal-access-tokens.component';
import { personalAccessTokensRoute } from 'app/shared/user-settings/personal-access-tokens/personal-access-tokens.route';

@NgModule({
    imports: [RouterModule.forChild(personalAccessTokensRoute), ArtemisSharedModule, ClipboardModule],
    declarations: [PersonalAccessTokensComponent],
})
export class PersonalAccessTokensModule {}
