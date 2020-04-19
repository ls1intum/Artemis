/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArtemisTestModule } from '../../../test.module';
import { ProgrammingExerciseComponent } from 'app/exercises/programming/shared/programming-exercise.component';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { MockSyncStorage } from '../../../mocks/mock-sync.storage';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../mocks/mock-translate.service';

describe('Component Tests', () => {
    describe('ProgrammingExercise Management Component', () => {
        let comp: ProgrammingExerciseComponent;
        let fixture: ComponentFixture<ProgrammingExerciseComponent>;
        let service: ProgrammingExerciseService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [ProgrammingExerciseComponent],
                providers: [
                    { provide: LocalStorageService, useClass: MockSyncStorage },
                    { provide: SessionStorageService, useClass: MockSyncStorage },
                    { provide: TranslateService, useClass: MockTranslateService },
                ],
            })
                .overrideTemplate(ProgrammingExerciseComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ProgrammingExerciseComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ProgrammingExerciseService);
            comp.route.params.subscribe((params) => {
                comp.instanceNumber = params['12345'];
            });
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new ProgrammingExercise(123)],
                        headers,
                    }),
                ),
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.programmingExercises[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
