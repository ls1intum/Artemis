import { Component } from '@angular/core';
import { LandingNavbarComponent } from './landing-navbar.component';
import { LandingHeroComponent } from './landing-hero.component';
import { LandingFeaturesComponent } from './landing-features.component';
import { LandingProgrammingExercisesComponent } from './landing-programming-exercises.component';
import { LandingExamsComponent } from './landing-exams.component';
import { LandingCtaComponent } from './landing-cta.component';
import { LandingFooterComponent } from './landing-footer.component';
import { LandingGlowbarComponent } from './landing-glowbar.component';

@Component({
    selector: 'jhi-landing',
    standalone: true,
    imports: [
        LandingNavbarComponent,
        LandingHeroComponent,
        LandingFeaturesComponent,
        LandingProgrammingExercisesComponent,
        LandingExamsComponent,
        LandingCtaComponent,
        LandingFooterComponent,
        LandingGlowbarComponent,
    ],
    template: `
        <div class="landing-page">
            <jhi-landing-navbar />
            <jhi-landing-glowbar />
            <jhi-landing-hero />
            <jhi-landing-glowbar />
            <section id="features">
                <jhi-landing-features />
            </section>
            <jhi-landing-glowbar />
            <section id="programming-exercises">
                <jhi-landing-programming-exercises />
            </section>
            <jhi-landing-glowbar />
            <section id="exams">
                <jhi-landing-exams />
            </section>
            <jhi-landing-glowbar />
            <jhi-landing-cta />
            <jhi-landing-footer />
        </div>
    `,
    styleUrl: './landing.component.scss',
})
export class LandingComponent {}
