import { async, ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { TeamExerciseSearchComponent } from 'app/exercises/shared/team/team-exercise-search/team-exercise-search.component';
import { MockCourseManagementService } from '../../helpers/mocks/service/mock-course-management.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';
import { TeamStudentSearchComponent } from 'app/exercises/shared/team/team-student-search/team-student-search.component';
import { SinonStub, stub } from 'sinon';
import * as sinonChai from 'sinon-chai';
import * as chai from 'chai';
import * as sinon from 'sinon';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { interval, Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { TeamSearchUser } from 'app/entities/team-search-user.model';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe.ts';
import { MockComponent, MockPipe } from 'ng-mocks';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { Team } from 'app/entities/team.model';
import { User } from 'app/core/user/user.model';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';
import { BrowserModule, By } from '@angular/platform-browser';
import { DebugElement, ElementRef } from '@angular/core';
import { map, take } from 'rxjs/operators';

chai.use(sinonChai);
//const expect = chai.expect;

describe('TeamStudentSearchComponent', () => {
    let comp: TeamStudentSearchComponent;
    let fixture: ComponentFixture<TeamStudentSearchComponent>;
    let searchInCourseForExerciseTeamStub: SinonStub;
    let debugElement: DebugElement;

    let teamService: TeamService;
    const inputFieldValue = 'input#student-search-input.form-control.open';
    const teamSearchUser1: TeamSearchUser = { id: 1, login: 'artemis_user_1', assignedTeamId: undefined };
    const teamSearchUser2: TeamSearchUser = { id: 2, login: 'artemis_user_2', assignedTeamId: undefined };
    const textMock$: Observable<string> = of('artemis');
    const searchReturnValue: Observable<HttpResponse<TeamSearchUser[]>> = of(new HttpResponse({ body: [teamSearchUser1, teamSearchUser2] }));

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot(), BrowserModule, FormsModule, NgbModule],
            declarations: [TeamStudentSearchComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        })
            .overrideTemplate(TeamExerciseSearchComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TeamStudentSearchComponent);
                comp = fixture.componentInstance;
                teamService = TestBed.inject(TeamService);
                debugElement = fixture.debugElement;
                searchInCourseForExerciseTeamStub = stub(teamService, 'searchInCourseForExerciseTeam').returns(searchReturnValue);
                comp.course = { id: 3 } as Course;
                comp.exercise = { id: 4 } as Exercise;
                comp.team = { id: 5 } as Team;
                comp.studentsFromPendingTeam = [{ id: 1, login: 'artemis_user_1' } as User];
                jest.useRealTimers();
            });
    });

    describe('onSearch', () => {
        it('should do ', async(() => {
            //todo create spies!
            //spyOn<any>(comp, 'userCanBeAddedToPendingTeam').and.callThrough();
            let child1 = {
                setAttribute: (name: string, value: string) => {
                    child1.disabled = value;
                },
                disabled: 'true',
            };
            let child2 = {
                setAttribute: (name: string, value: string) => {
                    child2.disabled = value;
                },
                disabled: 'true',
            };

            comp.ngbTypeahead = { nativeElement: { nextSibling: { children: [child1, child2] } } };
            comp.onSearch(textMock$).subscribe((teamSearchUsers) => {
                expect(teamSearchUsers.length).toBe(2);
            });
            //expect(comp['userCanBeAddedToPendingTeam']).toHaveBeenCalled();
        }));
    });
});
