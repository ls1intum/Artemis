import { Provider } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';

export function mockedActivatedRoute(params: any, queryParams: any = {}): Provider {
    return {
        provide: ActivatedRoute,
        useValue: {
            snapshot: {
                paramMap: convertToParamMap(params),
                queryParamMap: convertToParamMap(queryParams),
            },
        },
    };
}
