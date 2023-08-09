import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../../test.module';
import { MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltipMocksModule } from '../../../../helpers/mocks/directive/ngbTooltipMocks.module';
import { LectureUnitNodeDetailsComponent } from 'app/course/learning-paths/learning-path-graph/node-details/lecture-unit-node-details.component';
import { LectureService } from 'app/lecture/lecture.service';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';

describe('LectureUnitNodeDetailsComponent', () => {
    let fixture: ComponentFixture<LectureUnitNodeDetailsComponent>;
    let comp: LectureUnitNodeDetailsComponent;
    let lectureService: LectureService;
    let findWithDetailsStub: jest.SpyInstance;
    let lecture: Lecture;
    let lectureUnit: LectureUnit;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbTooltipMocksModule],
            declarations: [LectureUnitNodeDetailsComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LectureUnitNodeDetailsComponent);
                comp = fixture.componentInstance;
                lecture = new Lecture();
                lecture.id = 1;
                lectureUnit = new TextUnit();
                lectureUnit.id = 2;
                lectureUnit.name = 'Some arbitrary name';
                lecture.lectureUnits = [lectureUnit];

                lectureService = TestBed.inject(LectureService);
                findWithDetailsStub = jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(of(new HttpResponse({ body: lecture })));
                comp.lectureId = lecture.id;
                comp.lectureUnitId = lectureUnit.id;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load lecture unit on init if not present', () => {
        fixture.detectChanges();
        expect(findWithDetailsStub).toHaveBeenCalledOnce();
        expect(findWithDetailsStub).toHaveBeenCalledWith(lecture.id);
        expect(comp.lecture).toEqual(lecture);
        expect(comp.lectureUnit).toEqual(lectureUnit);
    });

    it('should not load lecture unit on init if already present', () => {
        comp.lecture = lecture;
        comp.lectureUnit = lectureUnit;
        fixture.detectChanges();
        expect(findWithDetailsStub).not.toHaveBeenCalled();
    });
});
