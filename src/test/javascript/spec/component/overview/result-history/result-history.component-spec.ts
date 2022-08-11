import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Result } from 'app/entities/result.model';
import { ResultHistoryComponent } from 'app/overview/result-history/result-history.component';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTestModule } from '../../../test.module';
import { faCheck, faTimes } from '@fortawesome/free-solid-svg-icons';

describe('ResultHistoryComponent', () => {
    let component: ResultHistoryComponent;
    let fixture: ComponentFixture<ResultHistoryComponent>;
    let result: Result;

    beforeEach(() => {
        result = new Result();

        return TestBed.configureTestingModule({
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

    it('should return the right values for result score', () => {
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
