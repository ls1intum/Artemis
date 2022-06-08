import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { TeamOwnerSearchComponent } from 'app/exercises/shared/team/team-owner-search/team-owner-search.component';
import { MockCourseManagementService } from '../../helpers/mocks/service/mock-course-management.service';
import { ArtemisTestModule } from '../../test.module';
import { User } from 'app/core/user/user.model';
import { NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgModel } from '@angular/forms';
import { HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

describe('Team Owner Search Component', () => {
    let comp: TeamOwnerSearchComponent;
    let fixture: ComponentFixture<TeamOwnerSearchComponent>;
    let courseService: CourseManagementService;

    const owner = { login: 'userLogin', name: 'name' } as User;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [TeamOwnerSearchComponent, MockDirective(NgbTypeahead), MockDirective(NgModel), MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: CourseManagementService, useClass: MockCourseManagementService }],
        }).compileComponents();

        fixture = TestBed.createComponent(TeamOwnerSearchComponent);
        comp = fixture.componentInstance;
        courseService = fixture.debugElement.injector.get(CourseManagementService);
    });

    it('should initialize with team owner', () => {
        comp.team = { owner };

        fixture.detectChanges();

        expect(comp.owner).toEqual(owner);
        expect(comp.owner).not.toBe(owner); // Should be deep cloned

        expect(comp.inputDisplayValue).toBe(`${owner.name} (${owner.login})`);
    });

    it('should search on input change and find a matching result', () => {
        const searchFailedSpy = jest.spyOn(comp.searchFailed, 'emit');
        const searchingSpy = jest.spyOn(comp.searching, 'emit');
        const searchNoResultsSpy = jest.spyOn(comp.searchNoResults, 'emit');

        const courseServiceSpy = jest.spyOn(courseService, 'getAllUsersInCourseGroup');
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
        const searchFailedSpy = jest.spyOn(comp.searchFailed, 'emit');
        const searchingSpy = jest.spyOn(comp.searching, 'emit');
        const searchNoResultsSpy = jest.spyOn(comp.searchNoResults, 'emit');

        const courseServiceSpy = jest.spyOn(courseService, 'getAllUsersInCourseGroup');
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
        const searchFailedSpy = jest.spyOn(comp.searchFailed, 'emit');

        const courseServiceSpy = jest.spyOn(courseService, 'getAllUsersInCourseGroup');
        courseServiceSpy.mockReturnValue(throwError(() => new Error('getAllUsersInCourseGroup failed')));

        comp.course = { id: 1 };

        let loadOwnerOptionsResult: User[] | undefined = [owner];
        comp.loadOwnerOptions().subscribe((result) => (loadOwnerOptionsResult = result));

        expect(searchFailedSpy).toHaveBeenCalledOnce();
        expect(searchFailedSpy).toHaveBeenCalledWith(true);

        expect(comp.ownerOptionsLoaded).toBeFalse();
        expect(loadOwnerOptionsResult).toBe(undefined);
    });
});
