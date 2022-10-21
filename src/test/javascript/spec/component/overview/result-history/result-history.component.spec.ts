import { ComponentFixture, TestBed } from '@angular/core/testing';
import { faCheck, faTimes } from '@fortawesome/free-solid-svg-icons';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { Result } from 'app/entities/result.model';
import { ResultHistoryComponent } from 'app/overview/result-history/result-history.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTestModule } from '../../../test.module';

describe('ResultHistoryComponent', () => {
    let component: ResultHistoryComponent;
    let fixture: ComponentFixture<ResultHistoryComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ResultHistoryComponent, MockPipe(ArtemisDatePipe), MockDirective(NgbTooltip)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ResultHistoryComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize with same rated results', () => {
        component.results = [
            { rated: true, id: 1 },
            { rated: true, id: 2 },
            { rated: true, id: 3 },
        ];
        component.ngOnChanges();
        expect(component.displayedResults).toEqual([
            { rated: true, id: 1 },
            { rated: true, id: 2 },
            { rated: true, id: 3 },
        ]);
        expect(component.showPreviousDivider).toBeFalse();
        expect(component.movedLastRatedResult).toBeFalsy();

        component.results = [
            { rated: false, id: 1 },
            { rated: false, id: 2 },
            { rated: false, id: 3 },
            { rated: false, id: 4 },
            { rated: false, id: 5 },
            { rated: false, id: 6 },
        ];
        component.ngOnChanges();
        expect(component.displayedResults).toEqual([
            { rated: false, id: 2 },
            { rated: false, id: 3 },
            { rated: false, id: 4 },
            { rated: false, id: 5 },
            { rated: false, id: 6 },
        ]);
        expect(component.showPreviousDivider).toBeTrue();
        expect(component.movedLastRatedResult).toBeFalsy();
    });

    it('should initialize with mixed rated results', () => {
        component.results = [
            { rated: true, id: 1 },
            { rated: false, id: 2 },
            { rated: false, id: 3 },
        ];
        component.ngOnChanges();
        expect(component.displayedResults).toEqual([
            { rated: true, id: 1 },
            { rated: false, id: 2 },
            { rated: false, id: 3 },
        ]);
        expect(component.showPreviousDivider).toBeFalse();
        expect(component.movedLastRatedResult).toBeFalsy();

        component.results = [
            { rated: true, id: 1 },
            { rated: false, id: 2 },
            { rated: false, id: 3 },
            { rated: false, id: 4 },
            { rated: false, id: 5 },
            { rated: false, id: 6 },
        ];
        component.ngOnChanges();
        expect(component.displayedResults).toEqual([
            { rated: true, id: 1 },
            { rated: false, id: 3 },
            { rated: false, id: 4 },
            { rated: false, id: 5 },
            { rated: false, id: 6 },
        ]);
        expect(component.showPreviousDivider).toBeTrue();
        expect(component.movedLastRatedResult).toBeTrue();
    });

    it('should return the right values for result score', () => {
        const result = new Result();

        fixture.detectChanges();
        result.score = 85;
        expect(component.resultIcon(result)).toEqual(faCheck);
        expect(component.resultClass(result)).toBe('success');

        result.score = 50;
        expect(component.resultIcon(result)).toEqual(faTimes);
        expect(component.resultClass(result)).toBe('warning');

        result.score = 30;
        expect(component.resultClass(result)).toBe('danger');
    });
});
