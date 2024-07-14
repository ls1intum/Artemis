import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { CompetenciesStudentPageComponent } from 'app/course/competencies/pages/competencies-student-page/competencies-student-page.component';
import { AlertService } from 'app/core/util/alert.service';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';

describe('CompetenciesStudentPageComponent', () => {
    let component: CompetenciesStudentPageComponent;
    let fixture: ComponentFixture<CompetenciesStudentPageComponent>;
    // let competencyApiService: CompetencyApiService;
    // let prerequisiteApiService: PrerequisiteApiService;
    // let alertService: AlertService;
    //
    // let getCompetenciesSpy: jest.SpyInstance;

    const courseId = 1;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CompetenciesStudentPageComponent],
            providers: [
                provideHttpClient(),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: of({
                                courseId: courseId,
                            }),
                        },
                    },
                },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useClass: MockAlertService },
            ],
        }).compileComponents();

        // competencyApiService = TestBed.inject(CompetencyApiService);
        // prerequisiteApiService = TestBed.inject(PrerequisiteApiService);
        // alertService = TestBed.inject(AlertService);
        //
        // getCompetenciesSpy = jest.spyOn(competencyApiService, 'getAllByCourseId').mockReturnValue(Promise.resolve([]));

        fixture = TestBed.createComponent(CompetenciesStudentPageComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
        expect(component.courseId()).toBe(courseId);
    });

    it('should load course competencies', () => {
        const loadDataSpy = jest.spyOn(component, 'loadData');
        const loadPrerequisitesSpy = jest.spyOn(component, 'loadPrerequisites');
        const loadCompetenciesSpy = jest.spyOn(component, 'loadCompetencies');

        fixture.detectChanges();

        expect(loadDataSpy).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(loadPrerequisitesSpy).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(loadCompetenciesSpy).toHaveBeenCalledExactlyOnceWith(courseId);
    });
});
