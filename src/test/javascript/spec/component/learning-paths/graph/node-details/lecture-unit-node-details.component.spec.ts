import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../../test.module';
import { MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltipMocksModule } from '../../../../helpers/mocks/directive/ngbTooltipMocks.module';
import { LectureUnitNodeDetailsComponent } from 'app/course/learning-paths/learning-path-graph/node-details/lecture-unit-node-details.component';
import { LectureUnitForLearningPathNodeDetailsDTO, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';

describe('LectureUnitNodeDetailsComponent', () => {
    let fixture: ComponentFixture<LectureUnitNodeDetailsComponent>;
    let comp: LectureUnitNodeDetailsComponent;
    let lectureUnitService: LectureUnitService;
    let getLectureUnitForLearningPathNodeDetailsStub: jest.SpyInstance;
    let lectureUnit: LectureUnitForLearningPathNodeDetailsDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbTooltipMocksModule],
            declarations: [LectureUnitNodeDetailsComponent, MockPipe(ArtemisTranslatePipe)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LectureUnitNodeDetailsComponent);
                comp = fixture.componentInstance;
                lectureUnit = new LectureUnitForLearningPathNodeDetailsDTO();
                lectureUnit.id = 1;
                lectureUnit.name = 'Some arbitrary name';
                lectureUnit.type = LectureUnitType.TEXT;

                lectureUnitService = TestBed.inject(LectureUnitService);
                getLectureUnitForLearningPathNodeDetailsStub = jest
                    .spyOn(lectureUnitService, 'getLectureUnitForLearningPathNodeDetails')
                    .mockReturnValue(of(new HttpResponse({ body: lectureUnit })));
                comp.lectureUnitId = lectureUnit.id;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load lecture unit on init if not present', () => {
        fixture.detectChanges();
        expect(getLectureUnitForLearningPathNodeDetailsStub).toHaveBeenCalledOnce();
        expect(getLectureUnitForLearningPathNodeDetailsStub).toHaveBeenCalledWith(lectureUnit.id);
        expect(comp.lectureUnit).toEqual(lectureUnit);
    });

    it('should not load lecture unit on init if already present', () => {
        comp.lectureUnit = lectureUnit;
        fixture.detectChanges();
        expect(getLectureUnitForLearningPathNodeDetailsStub).not.toHaveBeenCalled();
    });
});
