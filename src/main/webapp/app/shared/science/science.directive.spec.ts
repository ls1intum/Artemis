import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Component } from '@angular/core';
import { By } from '@angular/platform-browser';
import { ScienceEventType } from 'app/shared/science/science.model';
import { ScienceDirective } from 'app/shared/science/science.directive';
import { ScienceService } from 'app/shared/science/science.service';
import { provideHttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockLocalStorageService } from 'test/helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { provideHttpClientTesting } from '@angular/common/http/testing';

@Component({
    template: '<div [jhiScience]="ScienceEventType.LECTURE__OPEN"></div>',
    imports: [ScienceDirective],
})
class ScienceDirectiveComponent {
    protected readonly ScienceEventType = ScienceEventType;
}

describe('ScienceDirective', () => {
    let fixture: ComponentFixture<ScienceDirectiveComponent>;
    let scienceService: ScienceService;
    let logEventStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ScienceDirectiveComponent);
                scienceService = TestBed.inject(ScienceService);
                logEventStub = jest.spyOn(scienceService, 'logEvent');
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should log event on click', fakeAsync(() => {
        fixture.whenStable();
        const div = fixture.debugElement.query(By.css('div'));
        expect(div).not.toBeNull();
        div.nativeElement.dispatchEvent(new MouseEvent('click'));
        tick(10);
        expect(logEventStub).toHaveBeenCalledOnce();
    }));
});
