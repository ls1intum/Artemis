import { ComponentFixture, TestBed } from '@angular/core/testing';
import '@angular/localize/init';
import { CompetencyManagementTableComponent } from 'app/atlas/manage/competency-management/competency-management-table.component';
import { PrerequisiteService } from 'app/atlas/manage/prerequisite.service';
import { CompetencyService } from 'app/atlas/manage/competency.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/shared/service/alert.service';
import { MockNgbModalService } from '../../../helpers/mocks/service/mock-ngb-modal.service';
import { MockProvider } from 'ng-mocks';
import { CompetencyRelationType, CompetencyWithTailRelationDTO, CourseCompetencyType } from 'app/atlas/shared/entities/competency.model';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { MockActivatedRoute } from '../../../helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';

describe('CompetencyManagementTableComponent', () => {
    let fixture: ComponentFixture<CompetencyManagementTableComponent>;
    let component: CompetencyManagementTableComponent;

    let competencyService: CompetencyService;
    let prerequisiteService: PrerequisiteService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CompetencyManagementTableComponent],
            declarations: [],
            providers: [
                MockProvider(AlertService),
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                MockProvider(AlertService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CompetencyManagementTableComponent);
                component = fixture.componentInstance;

                competencyService = TestBed.inject(CompetencyService);
                prerequisiteService = TestBed.inject(PrerequisiteService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize values', () => {
        component.competencyType = CourseCompetencyType.COMPETENCY;
        component.ngOnInit();
        expect(component.service).toEqual(competencyService);

        component.competencyType = CourseCompetencyType.PREREQUISITE;
        component.ngOnInit();
        expect(component.service).toEqual(prerequisiteService);
    });

    it('should handle import all data', () => {
        fixture.componentRef.setInput('allCompetencies', []);
        fixture.detectChanges();
        component.courseCompetencies = [];

        const responseBody: CompetencyWithTailRelationDTO[] = [
            { competency: { id: 1 }, tailRelations: [] },
            { competency: { id: 2 }, tailRelations: [{ id: 3, tailCompetencyId: 2, headCompetencyId: 1, relationType: CompetencyRelationType.ASSUMES }] },
        ];

        component.updateDataAfterImportAll(responseBody);
        expect(component.courseCompetencies).toHaveLength(2);
    });

    it('should handle delete competency', () => {
        const deleteSpy = jest.spyOn(competencyService, 'delete').mockReturnValue(of(new HttpResponse<object>({ status: 200 })));
        const competencyDeletedNextSpy = jest.spyOn(component.competencyDeleted, 'next');
        const competency1 = { id: 1, type: CourseCompetencyType.COMPETENCY };
        const competency2 = { id: 2, type: CourseCompetencyType.COMPETENCY };
        component.service = competencyService;
        component.courseCompetencies = [competency1, competency2];

        component.deleteCompetency(1);
        expect(deleteSpy).toHaveBeenCalledOnce();
        // Assert that competencyDeleted.next is called with the correct id
        expect(competencyDeletedNextSpy).toHaveBeenCalledWith(1);
    });
});
