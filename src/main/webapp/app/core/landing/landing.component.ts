import { Component } from '@angular/core';
import { LandingNavbarComponent } from './landing-navbar.component';
import { LandingHeroComponent } from './landing-hero.component';
import { LandingFeatureNarrativeComponent } from './landing-feature-narrative.component';
import { LandingCtaComponent } from './landing-cta.component';
import { LandingFooterComponent } from './landing-footer.component';
import { LandingGlowbarComponent } from './landing-glowbar.component';

@Component({
    selector: 'jhi-landing',
    standalone: true,
    imports: [LandingNavbarComponent, LandingHeroComponent, LandingFeatureNarrativeComponent, LandingCtaComponent, LandingFooterComponent, LandingGlowbarComponent],
    template: `
        <div class="landing-page">
            <jhi-landing-navbar />
            <jhi-landing-glowbar />
            <jhi-landing-hero />
            <jhi-landing-glowbar />
            <section id="features">
                <jhi-landing-feature-narrative />
            </section>
            <jhi-landing-glowbar />
            <jhi-landing-cta />
            <jhi-landing-footer />
        </div>
    `,
    styleUrl: './landing.component.scss',
})
export class LandingComponent {}
