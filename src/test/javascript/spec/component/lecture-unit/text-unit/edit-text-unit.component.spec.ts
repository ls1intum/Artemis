import dayjs from 'dayjs/esm';

import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TextUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/text-unit-form/text-unit-form.component';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { TextUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/textUnit.service';
import { MockProvider } from 'ng-mocks';
import { EditTextUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/edit-text-unit/edit-text-unit.component';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { Alert, AlertService } from 'app/core/util/alert.service';

@Component({ selector: 'jhi-text-unit-form', template: '' })
class TextUnitFormStubComponent {
    @Input() isEditMode = false;
    @Input() formData: TextUnitFormData;
    @Output() formSubmitted: EventEmitter<TextUnitFormData> = new EventEmitter<TextUnitFormData>();
}

@Component({ selector: 'jhi-lecture-unit-layout', template: '<ng-content></ng-content>' })
class LectureUnitLayoutStubComponent {
    @Input() isLoading = false;
}

describe('EditTextUnitComponent', () => {
    let editTextUnitComponentFixture: ComponentFixture<EditTextUnitComponent>;
    let editTextUnitComponent: EditTextUnitComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [TextUnitFormStubComponent, LectureUnitLayoutStubComponent, EditTextUnitComponent],
            providers: [
                MockProvider(TextUnitService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        paramMap: of({
                            get: () => {
                                return { textUnitId: 1 };
                            },
                        }),
                        parent: {
                            parent: {
                                paramMap: of({
                                    get: () => {
                                        return { lectureId: 1 };
                                    },
                                }),
                            },
                        },
                    },
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                editTextUnitComponentFixture = TestBed.createComponent(EditTextUnitComponent);
                editTextUnitComponent = editTextUnitComponentFixture.componentInstance;
                const alertService = TestBed.inject(AlertService);
                jest.spyOn(alertService, 'error').mockReturnValue({ message: '' } as Alert);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });
    it('should initialize', fakeAsync(() => {
        editTextUnitComponentFixture.detectChanges();
        tick();
        expect(editTextUnitComponent).not.toBeNull();
    }));

    it('should set form data correctly', () => {
        const textUnitService = TestBed.inject(TextUnitService);

        const originalTextUnit: TextUnit = new TextUnit();
        originalTextUnit.id = 1;
        originalTextUnit.name = 'Test';
        originalTextUnit.releaseDate = dayjs().year(2010).month(3).date(5);
        originalTextUnit.content = 'Lorem Ipsum';

        const response: HttpResponse<TextUnit> = new HttpResponse({
            body: originalTextUnit,
            status: 200,
        });

        const findByIdStub = jest.spyOn(textUnitService, 'findById').mockReturnValue(of(response));

        const textUnitFormStubComponent: TextUnitFormStubComponent = editTextUnitComponentFixture.debugElement.query(By.directive(TextUnitFormStubComponent)).componentInstance;

        editTextUnitComponentFixture.detectChanges();
        return editTextUnitComponentFixture.whenStable().then(() => {
            expect(findByIdStub).toHaveBeenCalledOnce();
            expect(editTextUnitComponent.formData.name).toEqual(originalTextUnit.name);
            expect(editTextUnitComponent.formData.releaseDate).toEqual(originalTextUnit.releaseDate);
            expect(editTextUnitComponent.formData.content).toEqual(originalTextUnit.content);
            expect(textUnitFormStubComponent.formData).toEqual(editTextUnitComponent.formData);
        });
    });

    it('should send PUT request upon form submission and navigate', fakeAsync(() => {
        const router: Router = TestBed.inject(Router);
        const textUnitService = TestBed.inject(TextUnitService);

        const originalTextUnit: TextUnit = new TextUnit();
        originalTextUnit.id = 1;
        originalTextUnit.name = 'Test';
        originalTextUnit.releaseDate = dayjs().year(2010).month(3).date(5);
        originalTextUnit.content = 'Lorem Ipsum';

        const findByidResponse: HttpResponse<TextUnit> = new HttpResponse({
            body: originalTextUnit,
            status: 200,
        });

        const findByIdStub = jest.spyOn(textUnitService, 'findById').mockReturnValue(of(findByidResponse));

        const formDate: TextUnitFormData = {
            name: 'CHANGED',
            releaseDate: dayjs().year(2010).month(3).date(5),
            content: 'Lorem Ipsum',
        };
        const updatedTextUnit: TextUnit = new TextUnit();
        updatedTextUnit.id = 1;
        updatedTextUnit.name = formDate.name;
        updatedTextUnit.releaseDate = formDate.releaseDate;
        updatedTextUnit.content = formDate.content;

        const updateResponse: HttpResponse<TextUnit> = new HttpResponse({
            body: updatedTextUnit,
            status: 200,
        });
        const updatedStub = jest.spyOn(textUnitService, 'update').mockReturnValue(of(updateResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        editTextUnitComponentFixture.detectChanges();
        tick();

        const textUnitForm: TextUnitFormStubComponent = editTextUnitComponentFixture.debugElement.query(By.directive(TextUnitFormStubComponent)).componentInstance;
        textUnitForm.formSubmitted.emit(formDate);

        return editTextUnitComponentFixture.whenStable().then(() => {
            expect(findByIdStub).toHaveBeenCalledOnce();
            expect(updatedStub).toHaveBeenCalledOnce();
            expect(navigateSpy).toHaveBeenCalledOnce();
            navigateSpy.mockRestore();
        });
    }));
});
