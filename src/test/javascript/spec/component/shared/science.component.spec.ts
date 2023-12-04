import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { AbstractScienceComponent } from 'app/shared/science/science.component';
import { ArtemisTestModule } from '../../test.module';
import { ScienceDirective } from 'app/shared/science/science.direcrive';
import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventType } from 'app/shared/science/science.model';

@Component({ template: '' })
class ScienceComponent extends AbstractScienceComponent {
    constructor(scienceService: ScienceService) {
        super(scienceService, ScienceEventType.LECTURE__OPEN);
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
            declarations: [ScienceDirective, ScienceComponent],
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

    it('should log event on init', () => {
        expect(comp).toBeDefined();
        expect(logEventStub).toHaveBeenCalledOnce();
    });
});
