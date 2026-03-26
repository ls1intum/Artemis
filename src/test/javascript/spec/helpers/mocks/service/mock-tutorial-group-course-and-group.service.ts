import { signal } from '@angular/core';
import { vi } from 'vitest';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TutorialGroupDetailDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';

export class MockTutorialGroupCourseAndGroupService {
    tutorialGroup = signal<TutorialGroupDetailDTO | undefined>(undefined);
    course = signal<Course | undefined>(undefined);
    isTutorialGroupLoading = signal(false);
    isCourseLoading = signal(false);

    fetchTutorialGroup = vi.fn();
    fetchCourse = vi.fn();
}
