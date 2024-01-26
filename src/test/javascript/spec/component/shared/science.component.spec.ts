import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { AbstractScienceComponent } from 'app/shared/science/science.component';
import { ArtemisTestModule } from '../../test.module';
import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventType } from 'app/shared/science/science.model';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';

@Component({ template: '' })
class ScienceComponent extends AbstractScienceComponent {
    constructor(scienceService: ScienceService) {
        super(scienceService, ScienceEventType.LECTURE__OPEN);
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
            imports: [ArtemisTestModule],
            declarations: [ScienceComponent],
            providers: [{ provide: LocalStorageService, useClass: MockLocalStorageService }],
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
