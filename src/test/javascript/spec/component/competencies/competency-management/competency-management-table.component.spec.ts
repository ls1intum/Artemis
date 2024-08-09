import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import '@angular/localize/init';
import { CompetencyManagementTableComponent } from 'app/course/competencies/competency-management/competency-management-table.component';
import { PrerequisiteService } from 'app/course/competencies/prerequisite.service';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { MockNgbModalService } from '../../../helpers/mocks/service/mock-ngb-modal.service';
import { MockProvider } from 'ng-mocks';
import { CompetencyRelationType, CompetencyWithTailRelationDTO, CourseCompetencyType } from 'app/entities/competency.model';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

describe('CompetencyManagementTableComponent', () => {
    let fixture: ComponentFixture<CompetencyManagementTableComponent>;
    let component: CompetencyManagementTableComponent;

    let competencyService: CompetencyService;
    let prerequisiteService: PrerequisiteService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CompetencyManagementTableComponent, ArtemisTestModule, ArtemisSharedModule, ArtemisMarkdownModule],
            declarations: [],
            providers: [MockProvider(AlertService), { provide: NgbModal, useClass: MockNgbModalService }],
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
        component.courseCompetencies = [];
        component.relations = [];

        const responseBody: CompetencyWithTailRelationDTO[] = [
            { competency: { id: 1 }, tailRelations: [] },
            { competency: { id: 2 }, tailRelations: [{ id: 3, tailCompetencyId: 2, headCompetencyId: 1, relationType: CompetencyRelationType.ASSUMES }] },
        ];

        component.updateDataAfterImportAll(responseBody);
        expect(component.courseCompetencies).toHaveLength(2);
        expect(component.relations).toHaveLength(1);
    });

    it('should handle delete competency', () => {
        const deleteSpy = jest.spyOn(competencyService, 'delete').mockReturnValue(of(new HttpResponse<object>({ status: 200 })));
        const competencyDeletedNextSpy = jest.spyOn(component.competencyDeleted, 'next');
        const competency1 = { id: 1, type: CourseCompetencyType.COMPETENCY };
        const competency2 = { id: 2, type: CourseCompetencyType.COMPETENCY };
        component.service = competencyService;
        component.courseCompetencies = [competency1, competency2];
        component.relations = [{ id: 1, headCompetency: competency1, tailCompetency: competency2, type: CompetencyRelationType.ASSUMES }];

        component.deleteCompetency(1);
        expect(deleteSpy).toHaveBeenCalledOnce();
        // Assert that competencyDeleted.next is called with the correct id
        expect(competencyDeletedNextSpy).toHaveBeenCalledWith(1);
    });
});
