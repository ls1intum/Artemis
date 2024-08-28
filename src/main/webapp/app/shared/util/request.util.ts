import { HttpParams } from '@angular/common/http';

export const createRequestOption = (req?: any): HttpParams => {
    let options: HttpParams = new HttpParams();
    if (req) {
        Object.keys(req).forEach((key) => {
            if (key !== 'sort') {
                options = options.set(key, req[key]);
            }
        });
        if (req.sort) {
            req.sort.forEach((val: any) => {
                options = options.append('sort', val);
            });
        }
    }
    return options;
};

export const createNestedRequestOption = (req?: any, parentKey?: string): HttpParams => {
    let options: HttpParams = new HttpParams();
    if (req) {
        Object.keys(req).forEach((key) => {
            if (key !== 'sort') {
                const optionKey = parentKey ? `${parentKey}.${key}` : key;
                options = options.set(optionKey, req[key]);
            }
        });
        if (req.sort) {
            req.sort.forEach((val: any) => {
                const optionKey = parentKey ? `${parentKey}.sort` : 'sort';
                options = options.append(optionKey, val);
            });
        }
    }
    return options;
};
