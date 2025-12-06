import { ActivatedRoute } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { ErrorComponent } from 'app/core/layouts/error/error.component';
import { ReplaySubject } from 'rxjs';
import { TranslateService, TranslateStore } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ErrorComponent', () => {
    let routeData$: ReplaySubject<any>;

    beforeEach(async () => {
        routeData$ = new ReplaySubject(1);

        await TestBed.configureTestingModule({
            imports: [ErrorComponent],
            providers: [
                { provide: ActivatedRoute, useValue: { data: routeData$.asObservable() } },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: TranslateStore, useValue: {} },
            ],
        }).compileComponents();
    });

    it('should set flags based on route data', () => {
        const fixture = TestBed.createComponent(ErrorComponent);
        routeData$.next({ error403: true, error404: false, errorMessage: 'Oops' });

        fixture.detectChanges();
        const comp = fixture.componentInstance;

        expect(comp.error403).toBeTrue();
        expect(comp.error404).toBeFalsy();
        expect(comp.errorMessage).toBe('Oops');
    });

    it('should handle missing fields gracefully', () => {
        const fixture = TestBed.createComponent(ErrorComponent);
        routeData$.next({});

        fixture.detectChanges();
        const comp = fixture.componentInstance;

        expect(comp.error403).toBeUndefined();
        expect(comp.error404).toBeUndefined();
        expect(comp.errorMessage).toBeUndefined();
    });
});
