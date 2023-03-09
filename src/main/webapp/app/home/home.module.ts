import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { HomeComponent } from 'app/home/home.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { HOME_ROUTE } from './home.route';
import { Saml2LoginComponent } from './saml2-login/saml2-login.component';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild([HOME_ROUTE])],
    declarations: [HomeComponent, Saml2LoginComponent],
})
export class ArtemisHomeModule {}
