import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MockProvider } from 'ng-mocks';
import { describe, expect, it, vi } from 'vitest';
import { Course } from 'app/course/shared/entities/course.model';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { generateExampleTutorialGroupsConfigurationDTO } from 'test/helpers/sample/tutorialgroup/tutorialGroupsConfigurationExampleModels';
import { tutorialGroupsConfigurationSaveObserver } from './tutorial-groups-configuration-save.util';

describe('tutorialGroupsConfigurationSaveObserver', () => {
    it('converts and stores the configuration before navigating on success', () => {
        TestBed.configureTestingModule({ providers: [MockProvider(CourseStorageService), MockProvider(AlertService)] });
        const course = { id: 3 } as Course;
        const storage = TestBed.inject(CourseStorageService);
        const update = vi.spyOn(storage, 'updateCourse');
        const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
        const observer = tutorialGroupsConfigurationSaveObserver(() => course, storage, TestBed.inject(Router), TestBed.inject(AlertService), 'tutorial-groups-checklist');
        const configuration = generateExampleTutorialGroupsConfigurationDTO({});

        observer.next(configuration);

        expect(course.tutorialGroupsConfiguration?.id).toBe(configuration.id);
        expect(course.tutorialGroupsConfiguration?.tutorialPeriodStartInclusive?.isValid()).toBe(true);
        expect(update).toHaveBeenCalledExactlyOnceWith(course);
        expect(navigate).toHaveBeenCalledExactlyOnceWith(['/course-management', 3, 'tutorial-groups-checklist']);
        expect(update.mock.invocationCallOrder[0]).toBeLessThan(navigate.mock.invocationCallOrder[0]);
    });

    it('handles errors without publishing a configuration or navigating', () => {
        TestBed.configureTestingModule({ providers: [MockProvider(CourseStorageService), MockProvider(AlertService)] });
        const storage = TestBed.inject(CourseStorageService);
        const update = vi.spyOn(storage, 'updateCourse');
        const navigate = vi.spyOn(TestBed.inject(Router), 'navigate');
        const alert = vi.spyOn(TestBed.inject(AlertService), 'error');
        const observer = tutorialGroupsConfigurationSaveObserver(() => ({ id: 3 }) as Course, storage, TestBed.inject(Router), TestBed.inject(AlertService), 'tutorial-groups');
        observer.error(new HttpErrorResponse({ status: 403 }));
        expect(alert).toHaveBeenCalledExactlyOnceWith('error.http.403');
        expect(update).not.toHaveBeenCalled();
        expect(navigate).not.toHaveBeenCalled();
    });

    it('reads the current course when the save response arrives', () => {
        TestBed.configureTestingModule({ providers: [MockProvider(CourseStorageService), MockProvider(AlertService)] });
        const initialCourse = { id: 3 } as Course;
        let course = initialCourse;
        const storage = TestBed.inject(CourseStorageService);
        const update = vi.spyOn(storage, 'updateCourse');
        const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
        const observer = tutorialGroupsConfigurationSaveObserver(() => course, storage, TestBed.inject(Router), TestBed.inject(AlertService), 'tutorial-groups');
        course = { id: 4 } as Course;
        observer.next(generateExampleTutorialGroupsConfigurationDTO({}));
        expect(initialCourse.tutorialGroupsConfiguration).toBeUndefined();
        expect(update).toHaveBeenCalledExactlyOnceWith(course);
        expect(navigate).toHaveBeenCalledExactlyOnceWith(['/course-management', 4, 'tutorial-groups']);
    });
});
