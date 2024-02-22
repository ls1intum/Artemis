import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { CreateTextUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/create-text-unit/create-text-unit.component';
import { TextUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/text-unit-form/text-unit-form.component';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { TextUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/textUnit.service';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import dayjs from 'dayjs/esm';
import { By } from '@angular/platform-browser';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { HttpResponse } from '@angular/common/http';

@Component({ selector: 'jhi-text-unit-form', template: '' })
class TextUnitFormStubComponent {
    @Input() isEditMode = false;
    @Output() formSubmitted: EventEmitter<TextUnitFormData> = new EventEmitter<TextUnitFormData>();
}

@Component({ selector: 'jhi-lecture-unit-layout', template: '<ng-content />' })
class LectureUnitLayoutStubComponent {
    @Input() isLoading = false;
}

describe('CreateTextUnitComponent', () => {
    let createTextUnitComponentFixture: ComponentFixture<CreateTextUnitComponent>;
    let createTextUnitComponent: CreateTextUnitComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [TextUnitFormStubComponent, LectureUnitLayoutStubComponent, CreateTextUnitComponent],
            providers: [
                MockProvider(TextUnitService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            parent: {
                                paramMap: of({
                                    get: (key: string) => {
                                        switch (key) {
                                            case 'lectureId':
                                                return 1;
                                        }
                                    },
                                }),
                                parent: {
                                    paramMap: of({
                                        get: (key: string) => {
                                            switch (key) {
                                                case 'courseId':
                                                    return 1;
                                            }
                                        },
                                    }),
                                },
                            },
                        },
                    },
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                createTextUnitComponentFixture = TestBed.createComponent(CreateTextUnitComponent);
                createTextUnitComponent = createTextUnitComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', fakeAsync(() => {
        createTextUnitComponentFixture.detectChanges();
        tick();
        expect(createTextUnitComponent).not.toBeNull();
    }));

    it('should send POST request upon form submission and navigate', fakeAsync(() => {
        const router: Router = TestBed.inject(Router);
        const textUnitService = TestBed.inject(TextUnitService);
        const formDate: TextUnitFormData = {
            name: 'Test',
            releaseDate: dayjs().year(2010).month(3).date(5),
            content: 'Lorem Ipsum',
        };

        const persistedTextUnit: TextUnit = new TextUnit();
        persistedTextUnit.id = 1;
        persistedTextUnit.name = formDate.name;
        persistedTextUnit.releaseDate = formDate.releaseDate;
        persistedTextUnit.content = formDate.content;

        const response: HttpResponse<TextUnit> = new HttpResponse({
            body: persistedTextUnit,
            status: 200,
        });

        const createStub = jest.spyOn(textUnitService, 'create').mockReturnValue(of(response));
        const navigateSpy = jest.spyOn(router, 'navigate');

        createTextUnitComponentFixture.detectChanges();
        tick();

        const textUnitForm: TextUnitFormStubComponent = createTextUnitComponentFixture.debugElement.query(By.directive(TextUnitFormStubComponent)).componentInstance;
        textUnitForm.formSubmitted.emit(formDate);

        createTextUnitComponentFixture.whenStable().then(() => {
            expect(createStub).toHaveBeenCalledOnce();
            expect(navigateSpy).toHaveBeenCalledOnce();
            navigateSpy.mockRestore();
        });
    }));
});
