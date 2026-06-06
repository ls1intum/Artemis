import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ResultHistoryComponent } from 'app/exercise/result-history/result-history.component';
import { MockPipe } from 'ng-mocks';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('ResultHistoryComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ResultHistoryComponent;
    let fixture: ComponentFixture<ResultHistoryComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ResultHistoryComponent, MockPipe(ArtemisDatePipe)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useClass: MockDialogService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ResultHistoryComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
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
        expect(component.displayedResults()).toEqual([
            { rated: true, id: 1, participation },
            { rated: true, id: 2, participation },
            { rated: true, id: 3, participation },
        ]);
        expect(component.showPreviousDivider()).toBe(false);
        expect(component.movedLastRatedResult()).toBeFalsy();

        fixture.componentRef.setInput('results', [
            { rated: false, id: 1, participation },
            { rated: false, id: 2, participation },
            { rated: false, id: 3, participation },
            { rated: false, id: 4, participation },
            { rated: false, id: 5, participation },
            { rated: false, id: 6, participation },
        ]);
        fixture.detectChanges();
        expect(component.displayedResults()).toEqual([
            { rated: false, id: 2, participation },
            { rated: false, id: 3, participation },
            { rated: false, id: 4, participation },
            { rated: false, id: 5, participation },
            { rated: false, id: 6, participation },
        ]);
        expect(component.showPreviousDivider()).toBe(true);
        expect(component.movedLastRatedResult()).toBeFalsy();
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
        expect(component.displayedResults()).toEqual([
            { rated: true, id: 1, participation },
            { rated: false, id: 2, participation },
            { rated: false, id: 3, participation },
        ]);
        expect(component.showPreviousDivider()).toBe(false);
        expect(component.movedLastRatedResult()).toBeFalsy();

        fixture.componentRef.setInput('results', [
            { rated: true, id: 1, participation },
            { rated: false, id: 2, participation },
            { rated: false, id: 3, participation },
            { rated: false, id: 4, participation },
            { rated: false, id: 5, participation },
            { rated: false, id: 6, participation },
        ]);
        fixture.detectChanges();
        expect(component.displayedResults()).toEqual([
            { rated: true, id: 1, participation },
            { rated: false, id: 3, participation },
            { rated: false, id: 4, participation },
            { rated: false, id: 5, participation },
            { rated: false, id: 6, participation },
        ]);
        expect(component.showPreviousDivider()).toBe(true);
        expect(component.movedLastRatedResult()).toBe(true);
    });

    it('should move a visible last rated result without duplicating it', () => {
        const participation = { id: 1, submissions: [] };
        const results = [
            { rated: true, id: 1, participation },
            { rated: false, id: 2, participation },
            { rated: true, id: 3, participation },
            { rated: false, id: 4, participation },
            { rated: false, id: 5, participation },
            { rated: false, id: 6, participation },
        ];
        fixture.componentRef.setInput('participationInput', participation);
        fixture.componentRef.setInput('results', results);
        fixture.detectChanges();

        expect(component.displayedResults()).toEqual([results[2], results[1], results[3], results[4], results[5]]);
        expect(component.displayedResults().map((result) => result.id)).toEqual([3, 2, 4, 5, 6]);
        expect(component.showPreviousDivider()).toBe(true);
        expect(component.movedLastRatedResult()).toBe(true);
    });
});
