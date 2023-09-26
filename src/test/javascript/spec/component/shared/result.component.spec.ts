import { ArtemisTestModule } from '../../test.module';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ResultTemplateStatus } from 'app/exercises/shared/result/result.utils';
import { SimpleChange } from '@angular/core';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

describe('ResultComponent', () => {
    let comp: ResultComponent;
    let fixture: ComponentFixture<ResultComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ResultComponent, TranslatePipeMock],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ResultComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set template status to BUILDING if isBuilding changes to true even though participation changes', () => {
        comp.participation = {
            results: [],
        } as any as StudentParticipation;

        comp.isBuilding = false;
        comp.ngOnInit();
        expect(comp.templateStatus).toEqual(ResultTemplateStatus.NO_RESULT);

        const newParticipation = {
            results: [],
        } as any as StudentParticipation;

        comp.isBuilding = true;
        comp.participation = newParticipation;
        comp.ngOnChanges({
            isBuilding: { currentValue: true, previousValue: false } as any as SimpleChange,
            participation: { currentValue: newParticipation } as any as SimpleChange,
        });

        expect(comp.templateStatus).toEqual(ResultTemplateStatus.IS_BUILDING);
    });
});
