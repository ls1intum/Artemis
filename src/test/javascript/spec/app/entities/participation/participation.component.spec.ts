/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { ParticipationComponent } from '../../../../../../main/webapp/app/entities/participation/participation.component';
import { ParticipationService } from '../../../../../../main/webapp/app/entities/participation/participation.service';
import { Participation } from '../../../../../../main/webapp/app/entities/participation/participation.model';

describe('Component Tests', () => {

    describe('Participation Management Component', () => {
        let comp: ParticipationComponent;
        let fixture: ComponentFixture<ParticipationComponent>;
        let service: ParticipationService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ParticipationComponent],
                providers: [
                    ParticipationService
                ]
            })
            .overrideTemplate(ParticipationComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(ParticipationComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ParticipationService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new Participation(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.participations[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
