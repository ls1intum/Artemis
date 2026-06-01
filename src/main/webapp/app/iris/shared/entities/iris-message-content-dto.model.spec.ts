import { IrisMessageContentDTO } from 'app/iris/shared/entities/iris-message-content-dto.model';

describe('IrisMessageContentDTO', () => {
    it('should create text content DTO', () => {
        const content = 'Hello World';
        const dto = IrisMessageContentDTO.text(content);
        expect(dto.type).toBe('text');
        expect(dto.textContent).toBe(content);
        expect(dto.jsonContent).toBeUndefined();
    });

    it('should create json content DTO', () => {
        const content = '{"key": "value"}';
        const dto = IrisMessageContentDTO.json(content);
        expect(dto.type).toBe('json');
        expect(dto.jsonContent).toBe(content);
        expect(dto.textContent).toBeUndefined();
    });
});
