import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpResponse } from '@angular/common/http';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { firstValueFrom, of, throwError } from 'rxjs';
import { NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import dayjs from 'dayjs/esm';

import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { TeamExerciseSearchComponent } from 'app/exercise/team/team-exercise-search/team-exercise-search.component';
import { MockCourseManagementService } from 'test/helpers/mocks/service/mock-course-management.service';
import { Course } from 'app/course/shared/entities/course.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('Team Exercise Search Component', () => {
    setupTestBed({ zoneless: true });

    let comp: TeamExerciseSearchComponent;
    let fixture: ComponentFixture<TeamExerciseSearchComponent>;
    let courseService: CourseManagementService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TeamExerciseSearchComponent],
            providers: [
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideTemplate(TeamExerciseSearchComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(TeamExerciseSearchComponent);
        comp = fixture.componentInstance;
        courseService = TestBed.inject(CourseManagementService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('formats the search result with release date', () => {
        const title = 'My exercise';
        const releaseDate = dayjs();
        const dateFormat = 'YYYY-MM-DD';

        const exercise = new TextExercise(undefined, undefined);
        exercise.title = title;
        exercise.releaseDate = releaseDate;

        const expectedResult = `${title} (${releaseDate.format(dateFormat)})`;
        const result = comp.searchResultFormatter(exercise);

        expect(result).toEqual(expectedResult);
    });

    it('formats the search result without release date', () => {
        const title = 'My exercise';

        const exercise = new TextExercise(undefined, undefined);
        exercise.title = title;

        const expectedResult = title;
        const result = comp.searchResultFormatter(exercise);

        expect(result).toEqual(expectedResult);
    });

    it('onAutocompleteSelect', () => {
        const title = 'My exercise';

        const exercise = new TextExercise(undefined, undefined);
        exercise.title = title;

        const selectExerciseEmitSpy = vi.spyOn(comp.selectExercise, 'emit');
        const searchResultFormatterSpy = vi.spyOn(comp, 'searchResultFormatter').mockReturnValue(title);

        comp.onAutocompleteSelect(exercise);

        expect(searchResultFormatterSpy).toHaveBeenCalledWith(exercise);
        expect(comp.inputDisplayValue).toEqual(title);
        expect(selectExerciseEmitSpy).toHaveBeenCalledWith(exercise);
    });

    it('searchInputFormatter', () => {
        const testInputDisplayValue = 'Test';
        comp.inputDisplayValue = testInputDisplayValue;

        // Should return the current inputDisplayValue
        expect(comp.searchInputFormatter()).toEqual(testInputDisplayValue);
    });

    it('searchMatchesExercise with exact term', () => {
        const exercise = new TextExercise(undefined, undefined);
        exercise.title = 'My Exercise';

        const matchesExercise = comp.searchMatchesExercise('My Exercise', exercise);

        expect(matchesExercise).toBe(true);
    });

    it('searchMatchesExercise with lowercase term', () => {
        const exercise = new TextExercise(undefined, undefined);
        exercise.title = 'My Exercise';

        const matchesExercise = comp.searchMatchesExercise('my exercise', exercise);

        expect(matchesExercise).toBe(true);
    });

    it('searchMatchesExercise with partial term start', () => {
        const exercise = new TextExercise(undefined, undefined);
        exercise.title = 'My Exercise';

        const matchesExercise = comp.searchMatchesExercise('my', exercise);

        expect(matchesExercise).toBe(true);
    });

    it('searchMatchesExercise with partial term end', () => {
        const exercise = new TextExercise(undefined, undefined);
        exercise.title = 'My Exercise';

        const matchesExercise = comp.searchMatchesExercise('ercise', exercise);

        expect(matchesExercise).toBe(true);
    });

    it('searchMatchesExercise without whitespace', () => {
        const exercise = new TextExercise(undefined, undefined);
        exercise.title = 'My Exercise';

        const matchesExercise = comp.searchMatchesExercise('MyExercise', exercise);

        expect(matchesExercise).toBe(false);
    });

    it('searchMatchesExercise with incorrect searchTerm', () => {
        const exercise = new TextExercise(undefined, undefined);
        exercise.title = 'My Exercise';

        const matchesExercise = comp.searchMatchesExercise('Other Exercise', exercise);

        expect(matchesExercise).toBe(false);
    });

    it('successfully loads the exercise options', async () => {
        const course = new Course();
        course.id = 1;
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('ignoreExercises', []);

        expect(comp.exerciseOptions).toHaveLength(0);

        const exerciseOptions = await firstValueFrom(comp.loadExerciseOptions());

        expect(exerciseOptions).not.toBeNull();
        expect(exerciseOptions!.length).toBeGreaterThan(0);
    });

    it('filters loaded exercise options, emits search state and reuses cached options', async () => {
        const selectedExercise = new TextExercise(undefined, undefined);
        selectedExercise.id = 1;
        selectedExercise.title = 'Selected Exercise';
        selectedExercise.teamMode = true;

        const matchingExercise = new TextExercise(undefined, undefined);
        matchingExercise.id = 2;
        matchingExercise.title = 'Matching Team Exercise';
        matchingExercise.teamMode = true;

        const ignoredExercise = new TextExercise(undefined, undefined);
        ignoredExercise.id = 3;
        ignoredExercise.title = 'Ignored Team Exercise';
        ignoredExercise.teamMode = true;

        const individualExercise = new TextExercise(undefined, undefined);
        individualExercise.id = 4;
        individualExercise.title = 'Individual Exercise';
        individualExercise.teamMode = false;

        const course = new Course();
        course.id = 1;
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('ignoreExercises', [ignoredExercise]);
        const courseServiceSpy = vi
            .spyOn(courseService, 'findWithExercises')
            .mockReturnValue(of(new HttpResponse({ body: { exercises: [ignoredExercise, individualExercise, matchingExercise, selectedExercise] } })));
        vi.spyOn(comp.ngbTypeahead as unknown as { call: () => NgbTypeahead }, 'call').mockReturnValue({ isPopupOpen: () => false } as NgbTypeahead);
        const searchingSpy = vi.spyOn(comp.searching, 'emit');
        const searchFailedSpy = vi.spyOn(comp.searchFailed, 'emit');
        const searchNoResultsSpy = vi.spyOn(comp.searchNoResults, 'emit');

        const searchResult = await firstValueFrom(comp.onSearch(of('selected')));

        expect(searchResult).toEqual([selectedExercise]);
        expect(comp.exerciseOptions).toEqual([matchingExercise, selectedExercise]);
        expect(comp.exerciseOptionsLoaded).toBe(true);
        expect(courseServiceSpy).toHaveBeenCalledOnce();
        expect(searchFailedSpy).toHaveBeenCalledWith(false);
        expect(searchingSpy).toHaveBeenNthCalledWith(1, true);
        expect(searchingSpy).toHaveBeenNthCalledWith(2, false);
        expect(searchNoResultsSpy).toHaveBeenCalledWith(undefined);

        comp.click$.next('matching');
        const cachedSearchResult = await firstValueFrom(comp.onSearch(of('matching')));

        expect(cachedSearchResult).toEqual([matchingExercise]);
        expect(courseServiceSpy).toHaveBeenCalledOnce();
    });

    it('emits search-no-results when loaded options do not match the search term', async () => {
        const course = new Course();
        course.id = 1;
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('ignoreExercises', []);
        vi.spyOn(courseService, 'findWithExercises').mockReturnValue(of(new HttpResponse({ body: { exercises: [] } })));
        const searchNoResultsSpy = vi.spyOn(comp.searchNoResults, 'emit');

        const searchResult = await firstValueFrom(comp.onSearch(of('missing')));

        expect(searchResult).toEqual([]);
        expect(searchNoResultsSpy).toHaveBeenNthCalledWith(1, undefined);
        expect(searchNoResultsSpy).toHaveBeenNthCalledWith(2, 'missing');
    });

    it('handles loading failures and resets loaded options state', async () => {
        const course = new Course();
        course.id = 1;
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('ignoreExercises', []);
        vi.spyOn(courseService, 'findWithExercises').mockReturnValue(throwError(() => new Error('loading failed')));
        const searchFailedSpy = vi.spyOn(comp.searchFailed, 'emit');

        const exerciseOptions = await firstValueFrom(comp.loadExerciseOptions());

        expect(exerciseOptions).toBeNull();
        expect(comp.exerciseOptionsLoaded).toBe(false);
        expect(searchFailedSpy).toHaveBeenCalledOnce();
        expect(searchFailedSpy).toHaveBeenCalledWith(true);
    });
});
