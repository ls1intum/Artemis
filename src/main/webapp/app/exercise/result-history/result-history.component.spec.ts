import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ResultHistoryComponent } from 'app/exercise/result-history/result-history.component';
import { MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('ResultHistoryComponent', () => {
    let component: ResultHistoryComponent;
    let fixture: ComponentFixture<ResultHistoryComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ResultHistoryComponent, MockPipe(ArtemisDatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, MockProvider(NgbModal), provideHttpClient(), provideHttpClientTesting()],
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
        const participation = { id: 1, submissions: [] };
        fixture.componentRef.setInput('participationInput', participation);
        fixture.componentRef.setInput('results', [
            { rated: true, id: 1, participation },
            { rated: true, id: 2, participation },
            { rated: true, id: 3, participation },
        ]);
        fixture.detectChanges();
        component.ngOnChanges();
        expect(component.displayedResults).toEqual([
            { rated: true, id: 1, participation },
            { rated: true, id: 2, participation },
            { rated: true, id: 3, participation },
        ]);
        expect(component.showPreviousDivider).toBeFalse();
        expect(component.movedLastRatedResult).toBeFalsy();

        fixture.componentRef.setInput('results', [
            { rated: false, id: 1, participation },
            { rated: false, id: 2, participation },
            { rated: false, id: 3, participation },
            { rated: false, id: 4, participation },
            { rated: false, id: 5, participation },
            { rated: false, id: 6, participation },
        ]);
        fixture.detectChanges();
        component.ngOnChanges();
        expect(component.displayedResults).toEqual([
            { rated: false, id: 2, participation },
            { rated: false, id: 3, participation },
            { rated: false, id: 4, participation },
            { rated: false, id: 5, participation },
            { rated: false, id: 6, participation },
        ]);
        expect(component.showPreviousDivider).toBeTrue();
        expect(component.movedLastRatedResult).toBeFalsy();
    });

    it('should initialize with mixed rated results', () => {
        const participation = { id: 1, submissions: [] };
        fixture.componentRef.setInput('participationInput', participation);
        fixture.componentRef.setInput('results', [
            { rated: true, id: 1, participation },
            { rated: false, id: 2, participation },
            { rated: false, id: 3, participation },
        ]);
        fixture.detectChanges();
        component.ngOnChanges();
        expect(component.displayedResults).toEqual([
            { rated: true, id: 1, participation },
            { rated: false, id: 2, participation },
            { rated: false, id: 3, participation },
        ]);
        expect(component.showPreviousDivider).toBeFalse();
        expect(component.movedLastRatedResult).toBeFalsy();

        fixture.componentRef.setInput('results', [
            { rated: true, id: 1, participation },
            { rated: false, id: 2, participation },
            { rated: false, id: 3, participation },
            { rated: false, id: 4, participation },
            { rated: false, id: 5, participation },
            { rated: false, id: 6, participation },
        ]);
        fixture.detectChanges();
        component.ngOnChanges();
        expect(component.displayedResults).toEqual([
            { rated: true, id: 1, participation },
            { rated: false, id: 3, participation },
            { rated: false, id: 4, participation },
            { rated: false, id: 5, participation },
            { rated: false, id: 6, participation },
        ]);
        expect(component.showPreviousDivider).toBeTrue();
        expect(component.movedLastRatedResult).toBeTrue();
    });
});
