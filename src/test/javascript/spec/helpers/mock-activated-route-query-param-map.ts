import { Provider } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

export function mockedActivatedRoute(params: any, queryParams: any = {}): Provider {
    return {
        provide: ActivatedRoute,
        useValue: {
            paramMap: new BehaviorSubject(convertToParamMap(params)),
            queryParamMap: new BehaviorSubject(convertToParamMap(queryParams)),
        },
    };
}
