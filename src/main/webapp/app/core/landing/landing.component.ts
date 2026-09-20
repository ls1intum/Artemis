import { ChangeDetectionStrategy, Component } from '@angular/core';
import { LandingNavbarComponent } from 'app/core/landing/landing-navbar.component';
import { LandingHeroComponent } from 'app/core/landing/landing-hero.component';
import { LandingSpotlightComponent } from 'app/core/landing/landing-spotlight.component';
import { LandingSocialProofComponent } from 'app/core/landing/landing-social-proof.component';
import { LandingPillarsComponent } from 'app/core/landing/landing-pillars.component';
import { LandingFeaturesComponent } from 'app/core/landing/landing-features.component';
import { LandingTrustComponent } from 'app/core/landing/landing-trust.component';
import { LandingResearchComponent } from 'app/core/landing/landing-research.component';
import { LandingCommunityComponent } from 'app/core/landing/landing-community.component';
import { LandingFaqComponent } from 'app/core/landing/landing-faq.component';
import { LandingFooterComponent } from 'app/core/landing/landing-footer.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-landing',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        LandingNavbarComponent,
        LandingHeroComponent,
        LandingSpotlightComponent,
        LandingSocialProofComponent,
        LandingPillarsComponent,
        LandingFeaturesComponent,
        LandingTrustComponent,
        LandingResearchComponent,
        LandingCommunityComponent,
        LandingFaqComponent,
        LandingFooterComponent,
        TranslateDirective,
    ],
    templateUrl: './landing.component.html',
    styleUrls: ['./landing.component.scss'],
})
export class LandingComponent {}
