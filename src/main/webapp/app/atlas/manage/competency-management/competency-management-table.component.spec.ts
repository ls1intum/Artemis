import { ComponentFixture, TestBed } from '@angular/core/testing';
import '@angular/localize/init';
import { CompetencyManagementTableComponent } from 'app/atlas/manage/competency-management/competency-management-table.component';
import { PrerequisiteService } from 'app/atlas/manage/services/prerequisite.service';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/shared/service/alert.service';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { MockProvider } from 'ng-mocks';
import { CompetencyRelationType, CompetencyWithTailRelationDTO, CourseCompetency, CourseCompetencyType } from 'app/atlas/shared/entities/competency.model';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { getComponentInstanceFromFixture } from 'test/helpers/utils/general-test.utils';

@Component({
    template: `
        <jhi-competency-management-table
            [courseId]="signal(1)"
            [courseCompetencies]="courseCompetencies"
            [(allCompetencies)]="allCompetencies"
            [competencyType]="competencyType"
            [standardizedCompetenciesEnabled]="true"
            (competencyDeleted)="competencyDeleted($event)"
        />
    `,
    imports: [CompetencyManagementTableComponent],
})
class WrappedComponent {
    courseCompetencies: CourseCompetency[] = [];
    allCompetencies: CourseCompetency[] = [];
    competencyType = CourseCompetencyType.COMPETENCY;
    protected readonly CourseCompetencyType = CourseCompetencyType;

    competencyDeleted(competencyId: number) {}
}

describe('CompetencyManagementTableComponent', () => {
    let fixture: ComponentFixture<WrappedComponent>;
    let component: WrappedComponent;
    let competencyManagementTableComponent: CompetencyManagementTableComponent;

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
                fixture = TestBed.createComponent(WrappedComponent);
                component = fixture.componentInstance;
                competencyManagementTableComponent = getComponentInstanceFromFixture(fixture, CompetencyManagementTableComponent);

                competencyService = TestBed.inject(CompetencyService);
                prerequisiteService = TestBed.inject(PrerequisiteService);

                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize values', () => {
        component.competencyType = CourseCompetencyType.COMPETENCY;
        expect(competencyManagementTableComponent.service).toEqual(competencyService);

        component.competencyType = CourseCompetencyType.PREREQUISITE;
        expect(competencyManagementTableComponent.service).toEqual(prerequisiteService);
    });

    it('should handle import all data', () => {
        const responseBody: CompetencyWithTailRelationDTO[] = [
            { competency: { id: 1 }, tailRelations: [] },
            { competency: { id: 2 }, tailRelations: [{ id: 3, tailCompetencyId: 2, headCompetencyId: 1, relationType: CompetencyRelationType.ASSUMES }] },
        ];

        competencyManagementTableComponent.updateDataAfterImportAll(responseBody);
        expect(competencyManagementTableComponent.courseCompetencies()).toHaveLength(2);
    });

    it('should handle delete competency', () => {
        const deleteSpy = jest.spyOn(competencyService, 'delete').mockReturnValue(of(new HttpResponse<object>({ status: 200 })));
        const competencyDeletedSpy = jest.spyOn(component, 'competencyDeleted');
        const competency1 = { id: 1, type: CourseCompetencyType.COMPETENCY };
        const competency2 = { id: 2, type: CourseCompetencyType.COMPETENCY };
        competencyManagementTableComponent.service = competencyService;
        component.courseCompetencies = [competency1, competency2];

        competencyManagementTableComponent.deleteCompetency(1);
        expect(deleteSpy).toHaveBeenCalledOnce();
        // Assert that competencyDeleted.next is called with the correct id
        expect(competencyDeletedSpy).toHaveBeenCalledWith(1);
    });
});
