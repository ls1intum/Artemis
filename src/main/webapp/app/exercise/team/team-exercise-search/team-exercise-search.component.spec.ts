import { expect, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import dayjs from 'dayjs/esm';

import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { TeamExerciseSearchComponent } from 'app/exercise/team/team-exercise-search/team-exercise-search.component';
import { MockCourseManagementService } from 'test/helpers/mocks/service/mock-course-management.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('Team Exercise Search Component', () => {
    setupTestBed({ zoneless: true });
    let comp: TeamExerciseSearchComponent;
    let fixture: ComponentFixture<TeamExerciseSearchComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
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

        vi.spyOn(comp.selectExercise, 'emit');
        vi.spyOn(comp, 'searchResultFormatter').mockReturnValue(title);

        comp.onAutocompleteSelect(exercise);

        expect(comp.searchResultFormatter).toHaveBeenCalledWith(exercise);
        expect(comp.inputDisplayValue).toEqual(title);
        expect(comp.selectExercise.emit).toHaveBeenCalledWith(exercise);
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
        comp.course = new Course();
        comp.course.id = 1;
        comp.ignoreExercises = [];

        expect(comp.exerciseOptions).toHaveLength(0);

        await comp.loadExerciseOptions().subscribe((exerciseOptions) => {
            expect(exerciseOptions).not.toBeNull();
            expect(exerciseOptions!.length).toBeGreaterThan(0);
        });
    });
});
