/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { ApollonDiagramComponent } from 'app/entities/apollon-diagram/apollon-diagram.component';
import { ApollonDiagramService } from 'app/entities/apollon-diagram/apollon-diagram.service';
import { ApollonDiagram } from 'app/shared/model/apollon-diagram.model';

describe('Component Tests', () => {
    describe('ApollonDiagram Management Component', () => {
        let comp: ApollonDiagramComponent;
        let fixture: ComponentFixture<ApollonDiagramComponent>;
        let service: ApollonDiagramService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ApollonDiagramComponent],
                providers: []
            })
                .overrideTemplate(ApollonDiagramComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ApollonDiagramComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ApollonDiagramService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new ApollonDiagram(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.apollonDiagrams[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
