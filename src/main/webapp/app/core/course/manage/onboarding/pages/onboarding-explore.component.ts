import { Component, inject, input } from '@angular/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowRight, faBullseye, faChalkboardTeacher, faCode, faFileAlt, faQuestion, faRocket, faUsers } from '@fortawesome/free-solid-svg-icons';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATLAS, MODULE_FEATURE_EXAM, MODULE_FEATURE_LECTURE, MODULE_FEATURE_TUTORIALGROUP } from 'app/app.constants';

@Component({
    selector: 'jhi-onboarding-explore',
    templateUrl: './onboarding-explore.component.html',
    styleUrls: ['./_onboarding-pages.scss'],
    styles: [
        `
            :host {
                display: block;
            }

            .explore-grid {
                display: grid;
                grid-template-columns: repeat(3, 1fr);
                gap: 1.25rem;
                max-width: 920px;
                margin: 0 auto;
            }

            .explore-card {
                border: 1px solid var(--bs-border-color);
                border-radius: 0.5rem;
                text-align: center;
                padding: 1.75rem 1.25rem;
                transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
                position: relative;
                overflow: hidden;
                display: flex;
                flex-direction: column;
                align-items: center;
                text-decoration: none;
                color: inherit;
                cursor: pointer;
                background: var(--overview-card-nested-bg, var(--bs-body-bg));

                &::before {
                    content: '';
                    position: absolute;
                    top: 0;
                    left: 0;
                    right: 0;
                    height: 3px;
                    background: var(--card-accent, var(--bs-primary));
                    opacity: 0;
                    transition: opacity 0.25s ease;
                }

                &:hover {
                    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
                    transform: translateY(-1px);
                    border-color: transparent;

                    &::before {
                        opacity: 1;
                    }
                }

                h5 {
                    margin-bottom: 0.5rem;
                    font-weight: 600;
                }

                p {
                    color: var(--bs-secondary-color);
                    font-size: 0.88rem;
                    margin-bottom: 1rem;
                    line-height: 1.45;
                    flex: 1;
                }
            }

            .explore-card--exercises {
                --card-accent: #6366f1;
            }
            .explore-card--lectures {
                --card-accent: #0ea5e9;
            }
            .explore-card--tutorial {
                --card-accent: #8b5cf6;
            }
            .explore-card--exams {
                --card-accent: #f59e0b;
            }
            .explore-card--competencies {
                --card-accent: #10b981;
            }
            .explore-card--faqs {
                --card-accent: #ec4899;
            }

            .explore-icon-wrapper {
                width: 56px;
                height: 56px;
                border-radius: 0.5rem;
                display: inline-flex;
                align-items: center;
                justify-content: center;
                margin-bottom: 1rem;
                color: var(--bs-white);
                background: var(--card-accent, var(--bs-primary));
                box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
                font-size: 1.25rem;
            }

            .explore-link {
                font-weight: 600;
                font-size: 0.9rem;
                text-decoration: none;
                display: inline-flex;
                align-items: center;
                gap: 0.35rem;
                color: var(--card-accent, var(--bs-primary));
                transition: gap 0.2s ease;

                &:hover {
                    gap: 0.6rem;
                }
            }
        `,
    ],
    imports: [TranslateDirective, RouterLink, FaIconComponent],
})
export class OnboardingExploreComponent {
    private readonly profileService = inject(ProfileService);

    readonly course = input.required<Course>();
    readonly showOnboardingLink = input(false);

    readonly lectureEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_LECTURE);
    readonly atlasEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS);
    readonly examEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_EXAM);
    readonly tutorialGroupEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_TUTORIALGROUP);

    protected readonly faArrowRight = faArrowRight;
    protected readonly faBullseye = faBullseye;
    protected readonly faChalkboardTeacher = faChalkboardTeacher;
    protected readonly faCode = faCode;
    protected readonly faFileAlt = faFileAlt;
    protected readonly faQuestion = faQuestion;
    protected readonly faRocket = faRocket;
    protected readonly faUsers = faUsers;
}
