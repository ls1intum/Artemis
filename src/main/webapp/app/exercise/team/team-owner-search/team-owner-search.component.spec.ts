import { expect, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { TeamOwnerSearchComponent } from 'app/exercise/team/team-owner-search/team-owner-search.component';
import { MockCourseManagementService } from 'test/helpers/mocks/service/mock-course-management.service';
import { User } from 'app/core/user/user.model';
import { HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('Team Owner Search Component', () => {
    setupTestBed({ zoneless: true });
    let comp: TeamOwnerSearchComponent;
    let fixture: ComponentFixture<TeamOwnerSearchComponent>;
    let courseService: CourseManagementService;

    const owner = { login: 'userLogin', name: 'name' } as User;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TeamOwnerSearchComponent],
            providers: [
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TeamOwnerSearchComponent);
        comp = fixture.componentInstance;
        courseService = TestBed.inject(CourseManagementService);
    });

    it('should initialize with team owner', () => {
        comp.team = { owner };

        fixture.detectChanges();

        expect(comp.owner).toEqual(owner);
        expect(comp.owner).not.toBe(owner); // Should be deep cloned

        expect(comp.inputDisplayValue).toBe(`${owner.name} (${owner.login})`);
    });

    it('should search on input change and find a matching result', () => {
        const searchFailedSpy = vi.spyOn(comp.searchFailed, 'emit');
        const searchingSpy = vi.spyOn(comp.searching, 'emit');
        const searchNoResultsSpy = vi.spyOn(comp.searchNoResults, 'emit');

        const courseServiceSpy = vi.spyOn(courseService, 'getAllUsersInCourseGroup');
        courseServiceSpy.mockReturnValue(of(new HttpResponse({ body: [owner] })));

        const searchText = owner.login!;

        comp.course = { id: 1 };

        let onSearchResult: User[] | undefined = undefined;
        comp.onSearch(of(searchText)).subscribe((result) => (onSearchResult = result));

        expect(searchFailedSpy).toHaveBeenCalledOnce();
        expect(searchFailedSpy).toHaveBeenCalledWith(false);

        expect(searchingSpy).toHaveBeenCalledTimes(2);
        expect(searchingSpy).toHaveBeenNthCalledWith(1, true);
        expect(searchingSpy).toHaveBeenNthCalledWith(2, false);

        expect(searchNoResultsSpy).toHaveBeenCalledOnce();
        expect(searchNoResultsSpy).toHaveBeenCalledWith(undefined);

        expect(onSearchResult).toEqual([owner]);
    });

    it('should search on input change and find no result', () => {
        const searchFailedSpy = vi.spyOn(comp.searchFailed, 'emit');
        const searchingSpy = vi.spyOn(comp.searching, 'emit');
        const searchNoResultsSpy = vi.spyOn(comp.searchNoResults, 'emit');

        const courseServiceSpy = vi.spyOn(courseService, 'getAllUsersInCourseGroup');
        courseServiceSpy.mockReturnValue(of(new HttpResponse({ body: [owner] })));

        const searchText = 'SearchText';

        comp.course = { id: 1 };

        let onSearchResult: User[] | undefined = undefined;
        comp.onSearch(of(searchText)).subscribe((result) => (onSearchResult = result));

        expect(searchFailedSpy).toHaveBeenCalledOnce();
        expect(searchFailedSpy).toHaveBeenCalledWith(false);

        expect(searchingSpy).toHaveBeenCalledTimes(2);
        expect(searchingSpy).toHaveBeenNthCalledWith(1, true);
        expect(searchingSpy).toHaveBeenNthCalledWith(2, false);

        expect(searchNoResultsSpy).toHaveBeenCalledTimes(2);
        expect(searchNoResultsSpy).toHaveBeenNthCalledWith(1, undefined);
        expect(searchNoResultsSpy).toHaveBeenNthCalledWith(2, searchText);

        expect(onSearchResult).toEqual([]);
    });

    it('should handle error when loading owner options', () => {
        const searchFailedSpy = vi.spyOn(comp.searchFailed, 'emit');

        const courseServiceSpy = vi.spyOn(courseService, 'getAllUsersInCourseGroup');
        courseServiceSpy.mockReturnValue(throwError(() => new Error('getAllUsersInCourseGroup failed')));

        comp.course = { id: 1 };

        let loadOwnerOptionsResult: User[] | undefined = [owner];
        comp.loadOwnerOptions().subscribe((result) => (loadOwnerOptionsResult = result));

        expect(searchFailedSpy).toHaveBeenCalledOnce();
        expect(searchFailedSpy).toHaveBeenCalledWith(true);

        expect(comp.ownerOptionsLoaded).toBe(false);
        expect(loadOwnerOptionsResult).toBeUndefined();
    });
});
