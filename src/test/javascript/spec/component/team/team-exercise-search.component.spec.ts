import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';

import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { TeamExerciseSearchComponent } from 'app/exercises/shared/team/team-exercise-search/team-exercise-search.component';
import dayjs from 'dayjs/esm';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockCourseManagementService } from '../../helpers/mocks/service/mock-course-management.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';

describe('Team Exercise Search Component', () => {
    let comp: TeamExerciseSearchComponent;
    let fixture: ComponentFixture<TeamExerciseSearchComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [TeamExerciseSearchComponent],
            providers: [
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
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

        jest.spyOn(comp.selectExercise, 'emit');
        jest.spyOn(comp, 'searchResultFormatter').mockReturnValue(title);

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

        expect(matchesExercise).toBeTrue();
    });

    it('searchMatchesExercise with lowercase term', () => {
        const exercise = new TextExercise(undefined, undefined);
        exercise.title = 'My Exercise';

        const matchesExercise = comp.searchMatchesExercise('my exercise', exercise);

        expect(matchesExercise).toBeTrue();
    });

    it('searchMatchesExercise with partial term start', () => {
        const exercise = new TextExercise(undefined, undefined);
        exercise.title = 'My Exercise';

        const matchesExercise = comp.searchMatchesExercise('my', exercise);

        expect(matchesExercise).toBeTrue();
    });

    it('searchMatchesExercise with partial term end', () => {
        const exercise = new TextExercise(undefined, undefined);
        exercise.title = 'My Exercise';

        const matchesExercise = comp.searchMatchesExercise('ercise', exercise);

        expect(matchesExercise).toBeTrue();
    });

    it('searchMatchesExercise without whitespace', () => {
        const exercise = new TextExercise(undefined, undefined);
        exercise.title = 'My Exercise';

        const matchesExercise = comp.searchMatchesExercise('MyExercise', exercise);

        expect(matchesExercise).toBeFalse();
    });

    it('searchMatchesExercise with incorrect searchTerm', () => {
        const exercise = new TextExercise(undefined, undefined);
        exercise.title = 'My Exercise';

        const matchesExercise = comp.searchMatchesExercise('Other Exercise', exercise);

        expect(matchesExercise).toBeFalse();
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
