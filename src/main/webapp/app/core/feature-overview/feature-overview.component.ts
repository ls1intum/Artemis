import { NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { ExamFeature, INSTRUCTOR_FEATURES, STUDENT_FEATURES, TargetAudience } from 'app/core/feature-overview/feature-overview-data';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/** The public exam feature pages for students ({@code /features/students}) and instructors ({@code /features/instructors}). */
@Component({
    selector: 'jhi-feature-overview',
    templateUrl: './feature-overview.component.html',
    styleUrl: './feature-overview.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [NgOptimizedImage, FaIconComponent, RouterLink, TranslateDirective, ArtemisTranslatePipe, TumAetUiButtonDirective],
})
export class FeatureOverviewComponent implements OnInit {
    private readonly route = inject(ActivatedRoute);
    private readonly profileService = inject(ProfileService);

    protected readonly TargetAudience = TargetAudience;

    readonly targetAudience = signal(TargetAudience.INSTRUCTORS);
    readonly features = signal<readonly ExamFeature[]>([]);

    /** Chooses the audience from the route and the features that apply to this installation. */
    ngOnInit(): void {
        const audience = this.route.snapshot.url[0]?.toString() === 'students' ? TargetAudience.STUDENTS : TargetAudience.INSTRUCTORS;
        const signsInWithTumAccounts = this.profileService.getProfileInfo()?.accountName === 'TUM';
        const features = audience === TargetAudience.STUDENTS ? STUDENT_FEATURES : INSTRUCTOR_FEATURES;
        this.targetAudience.set(audience);
        this.features.set(features.filter((feature) => !feature.tumOnly || signsInWithTumAccounts));
    }

    /**
     * Scrolls to the details of a feature. The application shell scrolls its content area rather than the window, so this
     * scrolls the element into view instead of computing a window offset.
     *
     * @param key the feature whose details to show
     */
    scrollToFeature(key: string): void {
        document.getElementById('feature-' + key)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
}
