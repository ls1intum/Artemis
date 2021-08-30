import { ComponentFixture, TestBed } from '@angular/core/testing';
import * as sinonChai from 'sinon-chai';
import * as chai from 'chai';
import * as sinon from 'sinon';

import { ExampleSubmissionImportComponent } from 'app/exercises/shared/example-submission/example-submission-import/example-submission-import.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('ExampleSubmissionImportComponent', () => {
  let component: ExampleSubmissionImportComponent;
  let fixture: ComponentFixture<ExampleSubmissionImportComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ExampleSubmissionImportComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ExampleSubmissionImportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).to.be.ok;
  });
});
