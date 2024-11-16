import { getTransformationsFromExifData } from 'app/shared/image-cropper/utils/exif.utils';
import { ExifTransform } from 'app/shared/image-cropper/interfaces/exif-transform.interface';

describe('ExifUtils', () => {
    describe('getTransformationsFromExifData', () => {
        it('should return correct transformations for given EXIF rotation values', () => {
            const testCases: { input: number; expected: ExifTransform }[] = [
                { input: 2, expected: { rotate: 0, flip: true } },
                { input: 3, expected: { rotate: 2, flip: false } },
                { input: 4, expected: { rotate: 2, flip: true } },
                { input: 5, expected: { rotate: 1, flip: true } },
                { input: 6, expected: { rotate: 1, flip: false } },
                { input: 7, expected: { rotate: 3, flip: true } },
                { input: 8, expected: { rotate: 3, flip: false } },
                { input: 1, expected: { rotate: 0, flip: false } },
            ];

            testCases.forEach((testCase) => {
                const result = getTransformationsFromExifData(testCase.input);
                expect(result).toEqual(testCase.expected);
            });
        });

        it('should return correct transformations for given base64 image string', () => {
            const base64Image =
                'data:image/jpeg;base64,/9j/4QAiRXhpZgAATU0AKgAAAAgAAQESAAMAAAABAAYAAAAAAAD/2wCEAAEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAf/AABEIAAEAAgMBEQACEQEDEQH/xABKAAEAAAAAAAAAAAAAAAAAAAALEAEAAAAAAAAAAAAAAAAAAAAAAQEAAAAAAAAAAAAAAAAAAAAAEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwA/8H//2Q==';
            const result = getTransformationsFromExifData(base64Image);
            expect(result).toEqual({ rotate: 1, flip: false });
        });
    });
});
