import { Provider } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';

export function mockedActivatedRoute(params: any, queryParams: any = {}, data: any = {}, parentParams: any = {}): Provider {
    return {
        provide: ActivatedRoute,
        useValue: {
            data: of(data),
            paramMap: new BehaviorSubject(convertToParamMap(params)),
            queryParamMap: new BehaviorSubject(convertToParamMap(queryParams)),
            parent: {
                paramMap: new BehaviorSubject(convertToParamMap(parentParams)),
            },
        },
    };
}
