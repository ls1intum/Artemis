import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { AbstractScienceComponent } from 'app/shared/science/science.component';
import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventType } from 'app/shared/science/science.model';
import { MockLocalStorageService } from 'test/helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

@Component({ template: '' })
class ScienceComponent extends AbstractScienceComponent {
    constructor() {
        super(ScienceEventType.LECTURE__OPEN);
        super.logEvent();
    }
}

describe('AbstractScienceComponent', () => {
    let fixture: ComponentFixture<ScienceComponent>;
    let comp: ScienceComponent;
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
                scienceService = TestBed.inject(ScienceService);
                logEventStub = jest.spyOn(scienceService, 'logEvent');
                fixture = TestBed.createComponent(ScienceComponent);
                comp = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should log event on call', () => {
        expect(comp).toBeDefined();
        expect(logEventStub).toHaveBeenCalledOnce();
    });
});
