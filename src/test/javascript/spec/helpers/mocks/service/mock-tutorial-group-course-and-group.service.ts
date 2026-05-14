import { signal } from '@angular/core';
import { vi } from 'vitest';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TutorialGroupDetailData } from 'app/tutorialgroup/shared/entities/tutorial-group.model';

export class MockTutorialGroupCourseAndGroupService {
    tutorialGroup = signal<TutorialGroupDetailData | undefined>(undefined);
    course = signal<Course | undefined>(undefined);
    isTutorialGroupLoading = signal(false);
    isCourseLoading = signal(false);

    fetchTutorialGroup = vi.fn();
    fetchCourse = vi.fn();
}
