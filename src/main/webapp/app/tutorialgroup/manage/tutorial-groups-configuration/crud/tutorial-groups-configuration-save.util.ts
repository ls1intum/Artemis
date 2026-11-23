import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Course } from 'app/course/shared/entities/course.model';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { TutorialGroupConfiguration } from 'app/openapi/model/tutorial-group-configuration';
import { tutorialGroupsConfigurationEntityFromDto } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration-dto.model';

/** Publish a saved configuration and navigate only after a successful response. */
export function tutorialGroupsConfigurationSaveObserver(
    course: () => Course,
    courseStorage: CourseStorageService,
    router: Router,
    alertService: AlertService,
    destination: 'tutorial-groups-checklist' | 'tutorial-groups',
) {
    return {
        next: (configuration: TutorialGroupConfiguration) => {
            const currentCourse = course();
            currentCourse.tutorialGroupsConfiguration = tutorialGroupsConfigurationEntityFromDto(configuration);
            courseStorage.updateCourse(currentCourse);
            void router.navigate(['/course-management', currentCourse.id!, destination]);
        },
        error: (error: HttpErrorResponse) => onError(alertService, error),
    };
}
