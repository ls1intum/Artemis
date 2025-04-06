import { createNestedRequestOption } from 'app/shared/util/request.util';
import { HttpParams } from '@angular/common/http';

describe('createNestedRequestOption', () => {
    it('should create HttpParams with nested keys', () => {
        const req = { key1: 'value1', key2: 'value2' };
        const parentKey = 'parent';
        const params: HttpParams = createNestedRequestOption(req, parentKey);

        expect(params.get('parent.key1')).toBe('value1');
        expect(params.get('parent.key2')).toBe('value2');
    });

    it('should create HttpParams without parent key', () => {
        const req = { key1: 'value1', key2: 'value2' };
        const params: HttpParams = createNestedRequestOption(req);

        expect(params.get('key1')).toBe('value1');
        expect(params.get('key2')).toBe('value2');
    });

    it('should append sort parameters', () => {
        const req = { sort: ['value1', 'value2'] };
        const parentKey = 'parent';
        const params: HttpParams = createNestedRequestOption(req, parentKey);

        expect(params.getAll('parent.sort')).toEqual(['value1', 'value2']);
    });

    it('should handle empty request object', () => {
        const params: HttpParams = createNestedRequestOption();

        expect(params.keys()).toHaveLength(0);
    });
});
