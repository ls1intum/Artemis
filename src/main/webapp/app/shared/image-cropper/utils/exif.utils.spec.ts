import { getTransformationsFromExifData, supportsAutomaticRotation } from './exif.utils';

describe('Exif Utils', () => {
    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('supportsAutomaticRotation', () => {
        it('should resolve true if the browser supports automatic image orientation', async () => {
            const mockImage = {
                width: 1,
                height: 2,
                onload: null as (() => void) | null,
                src: '',
            } as unknown as HTMLImageElement;

            jest.spyOn(window, 'Image').mockImplementation(() => mockImage);

            const promise = supportsAutomaticRotation();
            mockImage.onload?.(new Event('load'));
            const result = await promise;

            expect(result).toBeTrue();
        });

        it('should resolve false if the browser does not support automatic image orientation', async () => {
            const mockImage = {
                width: 2,
                height: 1,
                onload: null as (() => void) | null,
                src: '',
            } as unknown as HTMLImageElement;

            jest.spyOn(window, 'Image').mockImplementation(() => mockImage);

            const promise = supportsAutomaticRotation();
            mockImage.onload?.(new Event('load'));
            const result = await promise;

            expect(result).toBeFalse();
        });
    });

    describe('getTransformationsFromExifData', () => {
        it('should return correct transformations for a given EXIF rotation value', () => {
            expect(getTransformationsFromExifData(6)).toEqual({ rotate: 1, flip: false });
            expect(getTransformationsFromExifData(3)).toEqual({ rotate: 2, flip: false });
            expect(getTransformationsFromExifData(8)).toEqual({ rotate: 3, flip: false });
            expect(getTransformationsFromExifData(2)).toEqual({ rotate: 0, flip: true });
        });

        it('should return default transformations for an invalid EXIF rotation value', () => {
            expect(getTransformationsFromExifData(0)).toEqual({ rotate: 0, flip: false });
            expect(getTransformationsFromExifData(-1)).toEqual({ rotate: 0, flip: false });
        });
    });
});
