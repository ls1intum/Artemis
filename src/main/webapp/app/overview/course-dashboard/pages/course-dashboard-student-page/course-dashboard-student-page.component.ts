import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { GeneralCourseInfoSectionComponent } from 'app/overview/course-dashboard/components/general-course-info-section/general-course-info-section.component';
import { IrisModule } from 'app/iris/iris.module';
import { CoursePerformanceSectionComponent } from 'app/overview/course-dashboard/components/course-performance-section/course-performance-section.component';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { PROFILE_IRIS } from 'app/app.constants';

@Component({
    selector: 'jhi-course-dashboard-student-page',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [GeneralCourseInfoSectionComponent, IrisModule, CoursePerformanceSectionComponent],
    templateUrl: './course-dashboard-student-page.component.html',
    styleUrl: './course-dashboard-student-page.component.scss',
})
export class CourseDashboardStudentPageComponent {
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly profileService = inject(ProfileService);
    private readonly irisSettingsService = inject(IrisSettingsService);

    readonly courseId = toSignal(this.activatedRoute.parent!.params.pipe(map((params) => Number(params.courseId))), { requireSync: true });
    private readonly profileInfo = toSignal(this.profileService.getProfileInfo());
    private readonly combinedCourseSettings = toSignal(this.irisSettingsService.getCombinedCourseSettings(this.courseId()));

    readonly irisEnabled = computed(() => {
        if (this.profileInfo()?.activeProfiles.includes(PROFILE_IRIS)) {
            return !!this.combinedCourseSettings()?.irisChatSettings?.enabled;
        }
        return false;
    });
}
