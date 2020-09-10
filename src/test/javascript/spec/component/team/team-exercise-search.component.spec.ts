import { TeamExerciseSearchComponent } from 'app/exercises/shared/team/team-exercise-search/team-exercise-search.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockCourseManagementService } from '../../helpers/mocks/service/mock-course-management.service';

describe('Team Exercise Search Component', () => {
    let comp: TeamExerciseSearchComponent;
    let fixture: ComponentFixture<TeamExerciseSearchComponent>;
    let service: CourseManagementService;

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
        service = fixture.debugElement.injector.get(CourseManagementService);
    });

    it('dummy test', () => {
        expect(true).toEqual(true);
    });
});
