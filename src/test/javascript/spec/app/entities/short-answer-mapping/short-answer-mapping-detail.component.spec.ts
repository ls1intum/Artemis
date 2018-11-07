/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerMappingDetailComponent } from 'app/entities/short-answer-mapping/short-answer-mapping-detail.component';
import { ShortAnswerMapping } from 'app/entities/short-answer-mapping/short-answer-mapping.model';

describe('Component Tests', () => {
    describe('ShortAnswerMapping Management Detail Component', () => {
        let comp: ShortAnswerMappingDetailComponent;
        let fixture: ComponentFixture<ShortAnswerMappingDetailComponent>;
        const route = ({ data: of({ shortAnswerMapping: new ShortAnswerMapping(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerMappingDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(ShortAnswerMappingDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ShortAnswerMappingDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.shortAnswerMapping).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
