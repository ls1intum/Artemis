import { Provider } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';

export function mockedActivatedRoute(params: any, queryParams: any = {}, data: any = {}, parentParams: any = {}, parentParentParams: any = {}): Provider {
    const parentParentParamMap = convertToParamMap(parentParentParams);
    const parentParamMap = convertToParamMap(parentParams);
    const paramMap = convertToParamMap(params);
    const queryParamMap = convertToParamMap(queryParams);

    return {
        provide: ActivatedRoute,
        useValue: {
            data: of(data),
            snapshot: {
                paramMap,
                queryParamMap,
                data,
            },
            paramMap: new BehaviorSubject(paramMap),
            queryParamMap: new BehaviorSubject(queryParamMap),
            parent: {
                snapshot: {
                    paramMap: parentParamMap,
                },
                paramMap: new BehaviorSubject(parentParamMap),
                parent: {
                    snapshot: {
                        paramMap: parentParentParamMap,
                    },
                    paramMap: new BehaviorSubject(parentParentParamMap),
                },
            },
        },
    };
}
