import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { HOME_ROUTE } from './home.route';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { HomeComponent } from 'app/home/home.component';
import { Saml2LoginComponent } from './saml2-login/saml2-login.component';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild([HOME_ROUTE])],
    declarations: [HomeComponent, Saml2LoginComponent],
})
export class ArtemisHomeModule {}
