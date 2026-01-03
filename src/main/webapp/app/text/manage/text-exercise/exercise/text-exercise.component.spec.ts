/**
 * Tests for TextExerciseComponent.
 * Verifies the component's behavior for managing text exercises in a course.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { TextExerciseComponent } from 'app/text/manage/text-exercise/exercise/text-exercise.component';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseFilter } from 'app/exercise/shared/entities/exercise/exercise-filter.model';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { ExerciseImportWrapperComponent } from 'app/exercise/import/exercise-import-wrapper/exercise-import-wrapper.component';
import { MockProvider } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';

describe('TextExercise Management Component', () => {
    setupTestBed({ zoneless: true });
    let comp: TextExerciseComponent;
    let fixture: ComponentFixture<TextExerciseComponent>;
    let courseExerciseService: CourseExerciseService;
    let modalService: NgbModal;

    const course = { id: 123 } as Course;
    const textExercise: TextExercise = { id: 456, title: 'Text Exercise', type: 'text' } as TextExercise;
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) }, queryParams: of({}) } as any as ActivatedRoute;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                LocalStorageService,
                SessionStorageService,
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                MockProvider(EventManager),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TextExerciseComponent);
        comp = fixture.componentInstance;
        courseExerciseService = TestBed.inject(CourseExerciseService);
        modalService = TestBed.inject(NgbModal);

        // Set exercises via internal property since textExercises is a signal input
        comp.internalTextExercises.set([textExercise]);
        // Initialize filter which is normally done in ngOnInit
        comp['filter'] = new ExerciseFilter();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should call loadExercises on init', () => {
        // GIVEN
        const headers = new HttpHeaders().append('link', 'link;link');
        vi.spyOn(courseExerciseService, 'findAllTextExercisesForCourse').mockReturnValue(
            of(
                new HttpResponse({
                    body: [textExercise],
                    headers,
                }),
            ),
        );

        // WHEN
        comp.course = course;
        comp.ngOnInit();

        // THEN
        expect(courseExerciseService.findAllTextExercisesForCourse).toHaveBeenCalledOnce();
    });

    it('should open import modal', () => {
        const mockReturnValue = {
            result: Promise.resolve({ id: 456 } as TextExercise),
            componentInstance: {},
        } as NgbModalRef;
        vi.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);

        // Set the course before opening the modal to ensure courseId is defined
        comp.course = course;

        comp.openImportModal();
        expect(modalService.open).toHaveBeenCalledWith(ExerciseImportWrapperComponent, { size: 'lg', backdrop: 'static' });
        expect(modalService.open).toHaveBeenCalledOnce();
        expect(mockReturnValue.componentInstance.exerciseType).toEqual(ExerciseType.TEXT);
    });

    it('should return exercise id', () => {
        expect(comp.trackId(0, textExercise)).toBe(456);
    });

    describe('TextExercise Search Exercises', () => {
        it('should show all exercises', () => {
            // WHEN
            comp.exerciseFilter = new ExerciseFilter('EXT', '', 'text');

            // THEN
            expect(comp.internalTextExercises()).toHaveLength(1);
            expect(comp.filteredTextExercises).toHaveLength(1);
        });

        it('should show no exercises', () => {
            // WHEN
            comp.exerciseFilter = new ExerciseFilter('Prog', '', 'all');

            // THEN
            expect(comp.internalTextExercises()).toHaveLength(1);
            expect(comp.filteredTextExercises).toHaveLength(0);
        });
    });

    it('should have working selection', () => {
        // WHEN
        comp.toggleExercise(textExercise);

        // THEN
        expect(comp.selectedExercises[0]).toMatchObject({ id: textExercise.id });
        expect(comp.allChecked).toEqual(comp.selectedExercises.length === comp.internalTextExercises().length);
    });
});
