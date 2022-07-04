import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTestModule } from '../../test.module';
import { TextExerciseComponent } from 'app/exercises/text/manage/text-exercise/text-exercise.component';
import { TextExercise } from 'app/entities/text-exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { Course } from 'app/entities/course.model';
import { ExerciseFilter } from 'app/entities/exercise-filter.model';
import { TextExerciseImportComponent } from 'app/exercises/text/manage/text-exercise-import.component';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';

describe('TextExercise Management Component', () => {
    let comp: TextExerciseComponent;
    let fixture: ComponentFixture<TextExerciseComponent>;
    let courseExerciseService: CourseExerciseService;
    let modalService: NgbModal;

    const course = { id: 123 } as Course;
    const textExercise: TextExercise = { id: 456, title: 'Text Exercise', type: 'text' } as TextExercise;
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [TextExerciseComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        })
            .overrideTemplate(TextExerciseComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(TextExerciseComponent);
        comp = fixture.componentInstance;
        courseExerciseService = fixture.debugElement.injector.get(CourseExerciseService);
        modalService = fixture.debugElement.injector.get(NgbModal);

        comp.textExercises = [textExercise];
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('Should call loadExercises on init', () => {
        // GIVEN
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(courseExerciseService, 'findAllTextExercisesForCourse').mockReturnValue(
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

    it('Should open modal', () => {
        const mockReturnValue = { result: Promise.resolve({ id: 456 } as TextExercise) } as NgbModalRef;
        jest.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);

        comp.openImportModal();
        expect(modalService.open).toHaveBeenCalledWith(TextExerciseImportComponent, { size: 'lg', backdrop: 'static' });
        expect(modalService.open).toHaveBeenCalledOnce();
    });

    it('Should return exercise id', () => {
        expect(comp.trackId(0, textExercise)).toBe(456);
    });

    describe('TextExercise Search Exercises', () => {
        it('Should show all exercises', () => {
            // WHEN
            comp.exerciseFilter = new ExerciseFilter('EXT', '', 'text');

            // THEN
            expect(comp.textExercises).toHaveLength(1);
            expect(comp.filteredTextExercises).toHaveLength(1);
        });

        it('Should show no exercises', () => {
            // WHEN
            comp.exerciseFilter = new ExerciseFilter('Prog', '', 'all');

            // THEN
            expect(comp.textExercises).toHaveLength(1);
            expect(comp.filteredTextExercises).toHaveLength(0);
        });
    });
});
