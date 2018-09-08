/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { ParticipationComponent } from 'app/entities/participation/participation.component';
import { ParticipationService } from 'app/entities/participation/participation.service';
import { Participation } from 'app/shared/model/participation.model';

describe('Component Tests', () => {
    describe('Participation Management Component', () => {
        let comp: ParticipationComponent;
        let fixture: ComponentFixture<ParticipationComponent>;
        let service: ParticipationService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ParticipationComponent],
                providers: []
            })
                .overrideTemplate(ParticipationComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ParticipationComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ParticipationService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new Participation(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.participations[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
