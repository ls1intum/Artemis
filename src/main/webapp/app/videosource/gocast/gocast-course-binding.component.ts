import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { GocastBinding, GocastCourse } from './gocast.model';
import { GocastService } from './gocast.service';

/** A single option in the TUM Live course p-select dropdown. */
interface GocastCourseOption {
    label: string;
    value: number;
    slug: string;
}

/**
 * Stage 1 — Course management (binding UI).
 *
 * Embedded in course management settings to let an instructor:
 *  1. Pick a TUM Live course from a dropdown (EP1-backed list).
 *  2. Create a PENDING binding (POST) and click "Authorize on TUM Live" (opens approvalUrl in new tab).
 *  3. On return, click "Check binding status" → the server calls EP7 and flips to ACTIVE.
 *  4. Revoke the binding (DELETE).
 */
@Component({
    selector: 'jhi-gocast-course-binding',
    templateUrl: './gocast-course-binding.component.html',
    imports: [FormsModule, TranslateDirective, ArtemisTranslatePipe, ButtonModule, CardModule, MessageModule, SelectModule],
})
export class GocastCourseBindingComponent implements OnInit {
    private readonly gocastService = inject(GocastService);
    private readonly alertService = inject(AlertService);

    /** The Artemis course id, passed from the parent (course detail / settings page). */
    courseId = input.required<number>();

    // ── state ───────────────────────────────────────────────────────────────
    tumLiveCourses = signal<GocastCourse[]>([]);
    selectedGocastCourseId = signal<number | undefined>(undefined);
    selectedGocastCourseSlug = signal<string | undefined>(undefined);
    binding = signal<GocastBinding | undefined>(undefined);
    /** The TUM Live approval URL returned by POST /binding (separate from the binding DTO). */
    approvalUrl = signal<string | undefined>(undefined);
    isLoadingCourses = signal(false);
    isCreatingBinding = signal(false);
    isCheckingStatus = signal(false);
    isRevoking = signal(false);

    /** Course list mapped to PrimeNG p-select options (label/value/slug). */
    courseOptions = computed<GocastCourseOption[]>(() =>
        this.tumLiveCourses().map((course) => ({
            label: `${course.name} (${course.teachingTerm}${course.year})`,
            value: course.id,
            slug: course.slug,
        })),
    );

    ngOnInit(): void {
        this.loadExistingBinding();
        this.loadTumLiveCourses();
    }

    private loadTumLiveCourses(): void {
        this.isLoadingCourses.set(true);
        this.gocastService.listAdministeredTumLiveCourses(this.courseId()).subscribe({
            next: (courses) => {
                this.tumLiveCourses.set(courses);
                this.isLoadingCourses.set(false);
            },
            error: () => {
                this.isLoadingCourses.set(false);
                this.alertService.error('artemisApp.gocast.binding.error.loadCourses');
            },
        });
    }

    private loadExistingBinding(): void {
        this.gocastService.getBinding(this.courseId()).subscribe({
            next: (binding) => {
                this.binding.set(binding);
            },
            error: () => {
                // 404 = no binding yet, that is fine — leave binding undefined
            },
        });
    }

    /**
     * Invoked when the instructor selects a TUM Live course from the dropdown.
     * Records the selected id and slug from the courses list.
     */
    onCourseSelected(gocastCourseId: number | undefined): void {
        if (gocastCourseId === undefined || gocastCourseId === null) {
            // Cleared selection — reset.
            this.selectedGocastCourseId.set(undefined);
            this.selectedGocastCourseSlug.set(undefined);
            return;
        }
        const course = this.tumLiveCourses().find((c) => c.id === gocastCourseId);
        if (course) {
            this.selectedGocastCourseId.set(course.id);
            this.selectedGocastCourseSlug.set(course.slug);
        }
    }

    /**
     * Creates the PENDING binding and returns the approvalUrl.
     * The instructor must then click "Authorize on TUM Live" to open the approval page.
     */
    createBinding(): void {
        const gocastCourseId = this.selectedGocastCourseId();
        const gocastCourseSlug = this.selectedGocastCourseSlug();
        if (!gocastCourseId || !gocastCourseSlug) {
            return;
        }
        this.isCreatingBinding.set(true);
        this.gocastService.createBinding(this.courseId(), gocastCourseId, gocastCourseSlug).subscribe({
            next: (response) => {
                // Server returns GocastBindingWithApprovalDTO: { binding, approvalUrl }
                this.binding.set(response.binding);
                this.approvalUrl.set(response.approvalUrl);
                this.isCreatingBinding.set(false);
                this.alertService.success('artemisApp.gocast.binding.pendingCreated');
            },
            error: () => {
                this.isCreatingBinding.set(false);
                this.alertService.error('artemisApp.gocast.binding.error.createBinding');
            },
        });
    }

    /**
     * Opens the TUM Live approval page in a new tab.
     * Called after a PENDING binding has been created.
     */
    openApprovalPage(): void {
        const url = this.approvalUrl();
        if (url) {
            window.open(url, '_blank', 'noopener,noreferrer');
        }
    }

    /**
     * Verifies the binding server-side (triggers EP7 on the server).
     * Server flips PENDING→ACTIVE when TUM Live confirms.
     * Called when the instructor returns from the approval page.
     */
    checkBindingStatus(): void {
        this.isCheckingStatus.set(true);
        this.gocastService.getBinding(this.courseId()).subscribe({
            next: (binding) => {
                this.binding.set(binding);
                this.isCheckingStatus.set(false);
                if (binding.status === 'ACTIVE') {
                    this.alertService.success('artemisApp.gocast.binding.active');
                } else if (binding.status === 'PENDING') {
                    this.alertService.info('artemisApp.gocast.binding.stillPending');
                }
            },
            error: () => {
                this.isCheckingStatus.set(false);
                this.alertService.error('artemisApp.gocast.binding.error.checkStatus');
            },
        });
    }

    /**
     * Revokes the current binding.
     */
    revokeBinding(): void {
        this.isRevoking.set(true);
        this.gocastService.deleteBinding(this.courseId()).subscribe({
            next: () => {
                this.binding.set(undefined);
                this.approvalUrl.set(undefined);
                this.isRevoking.set(false);
                this.alertService.success('artemisApp.gocast.binding.revoked');
            },
            error: () => {
                this.isRevoking.set(false);
                this.alertService.error('artemisApp.gocast.binding.error.revoke');
            },
        });
    }
}
