import { Component } from '@angular/core';
import { LandingNavbarComponent } from 'app/core/landing/landing-navbar.component';
import { LandingHeroComponent } from 'app/core/landing/landing-hero.component';
import { LandingSpotlightComponent } from 'app/core/landing/landing-spotlight.component';
import { LandingSocialProofComponent } from 'app/core/landing/landing-social-proof.component';
import { LandingFeaturesComponent } from 'app/core/landing/landing-features.component';
import { LandingFaqComponent } from 'app/core/landing/landing-faq.component';
import { LandingFooterComponent } from 'app/core/landing/landing-footer.component';

@Component({
    selector: 'jhi-landing',
    standalone: true,
    imports: [
        LandingNavbarComponent,
        LandingHeroComponent,
        LandingSpotlightComponent,
        LandingSocialProofComponent,
        LandingFeaturesComponent,
        LandingFaqComponent,
        LandingFooterComponent,
    ],
    templateUrl: './landing.component.html',
    styleUrls: ['./landing.component.scss'],
})
export class LandingComponent {}
