/// <reference path="../../../../node_modules/@types/jasmine/index.d.ts" />
import 'core-js';
import 'zone.js/dist/zone';
import 'zone.js/dist/long-stack-trace-zone';
import 'zone.js/dist/async-test';
import 'zone.js/dist/fake-async-test';
import 'zone.js/dist/sync-test';
import 'zone.js/dist/proxy';
import 'zone.js/dist/jasmine-patch';
import 'rxjs';
import 'intl/locale-data/jsonp/en-US.js';
import { TestBed } from '@angular/core/testing';
import { BrowserDynamicTestingModule, platformBrowserDynamicTesting } from '@angular/platform-browser-dynamic/testing';

TestBed.initTestEnvironment(BrowserDynamicTestingModule, platformBrowserDynamicTesting());

declare let require: any;
const testsContext: any = require.context('./', true, /\.spec/);
testsContext.keys().forEach(testsContext);

// these two lines solve typescript errors, because both, jest and jasmine define expect, simply import this combined expect to make sure Typescript does not complain
type JestExpect = <T>(actual: T) => jest.Matchers<T> & jasmine.Matchers<T>;
export declare const expect: JestExpect;
