import { ProgrammingExercisePagingService } from 'app/exercises/programming/manage/services/programming-exercise-paging.service';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { Subject } from 'rxjs';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from '../../helpers/mocks/service/mock-feature-toggle.service';
import { ProgrammingExerciseImportComponent } from 'app/exercises/programming/manage/programming-exercise-import.component';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { SearchResult } from 'app/shared/table/pageable-table';
import { ButtonComponent } from 'app/shared/components/button.component';
import { MockProgrammingExercisePagingService } from '../../helpers/mocks/service/mock-programming-exercise-paging.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { NgModel } from '@angular/forms';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { NgbHighlight, NgbPagination, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseCourseTitlePipe } from 'app/shared/pipes/exercise-course-title.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import '@angular/localize/init';

describe('ProgrammingExerciseImportComponent', () => {
    let comp: ProgrammingExerciseImportComponent;
    let fixture: ComponentFixture<ProgrammingExerciseImportComponent>;
    let debugElement: DebugElement;
    let pagingService: ProgrammingExercisePagingService;

    let pagingStub: jest.SpyInstance;

    const basicCourse = { id: 12, title: 'Random course title' } as Course;
    const exercise = { id: 42, title: 'Exercise title', programmingLanguage: ProgrammingLanguage.JAVA, course: basicCourse } as ProgrammingExercise;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, FeatureToggleModule],
            declarations: [
                ProgrammingExerciseImportComponent,
                ButtonComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgModel),
                MockDirective(SortDirective),
                MockComponent(FaIconComponent),
                MockDirective(SortByDirective),
                MockDirective(NgbHighlight),
                NgbPagination, // do not mock this directive as we need it for the test
                MockPipe(ExerciseCourseTitlePipe),
                MockDirective(NgbTooltip),
                MockDirective(TranslateDirective),
            ],
            providers: [
                { provide: ProgrammingExercisePagingService, useClass: MockProgrammingExercisePagingService },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseImportComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                pagingService = debugElement.injector.get(ProgrammingExercisePagingService);
                pagingStub = jest.spyOn(pagingService, 'searchForExercises');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should parse the pageable search result into the correct state', fakeAsync(() => {
        const searchResult = { resultsOnPage: [exercise], numberOfPages: 3 } as SearchResult<ProgrammingExercise>;
        const searchObservable = new Subject<SearchResult<ProgrammingExercise>>();
        pagingStub.mockReturnValue(searchObservable);

        fixture.detectChanges();
        tick();

        expect(pagingStub).toHaveBeenCalledTimes(1);
        searchObservable.next(searchResult);

        fixture.detectChanges();
        tick();

        expect(comp.content.numberOfPages).toBe(3);
        expect(comp.content.resultsOnPage[0].id).toBe(42);
        expect(comp.total).toBe(30);
        expect(comp.loading).toBeFalse();
    }));
});
