import { CropperSettings } from 'app/shared/image-cropper/interfaces/cropper.settings';
import { CropService } from 'app/shared/image-cropper/services/crop.service';

describe('CropService', () => {
    let service: CropService;
    let settings: CropperSettings;

    beforeEach(() => {
        service = new CropService();
        settings = new CropperSettings();
    });

    describe('#getResizeRatio', () => {
        describe('when onlyScaleDown is false', () => {
            it('when resizeToWidth is set', () => {
                settings.setOptions({
                    resizeToWidth: 500,
                    resizeToHeight: 0,
                    onlyScaleDown: false,
                });

                expect(service.getResizeRatio(1000, 123, settings)).toBe(0.5);
                expect(service.getResizeRatio(1000, 1234, settings)).toBe(0.5);
                expect(service.getResizeRatio(250, 123, settings)).toBe(2);
                expect(service.getResizeRatio(250, 1234, settings)).toBe(2);
            });
            it('when resizeToHeight is set', () => {
                settings.setOptions({
                    resizeToWidth: 0,
                    resizeToHeight: 500,
                    onlyScaleDown: false,
                });

                expect(service.getResizeRatio(123, 1000, settings)).toBe(0.5);
                expect(service.getResizeRatio(1234, 1000, settings)).toBe(0.5);
                expect(service.getResizeRatio(123, 250, settings)).toBe(2);
                expect(service.getResizeRatio(1234, 250, settings)).toBe(2);
            });
            it('when resizeToWidth and resizeToHeight is set', () => {
                settings.setOptions({
                    resizeToWidth: 500,
                    resizeToHeight: 500,
                    onlyScaleDown: false,
                });
                expect(service.getResizeRatio(1000, 2000, settings)).toBe(0.25);
                expect(service.getResizeRatio(2000, 1000, settings)).toBe(0.25);
                expect(service.getResizeRatio(250, 125, settings)).toBe(2);
                expect(service.getResizeRatio(125, 250, settings)).toBe(2);

                settings.setOptions({
                    resizeToWidth: 1000,
                    resizeToHeight: 500,
                    onlyScaleDown: false,
                });
                expect(service.getResizeRatio(1000, 2000, settings)).toBe(0.25);
                expect(service.getResizeRatio(1000, 1000, settings)).toBe(0.5);
                expect(service.getResizeRatio(1000, 500, settings)).toBe(1);
                expect(service.getResizeRatio(1000, 250, settings)).toBe(1);
                expect(service.getResizeRatio(123, 250, settings)).toBe(2);
                expect(service.getResizeRatio(500, 123, settings)).toBe(2);

                settings.setOptions({
                    resizeToWidth: 500,
                    resizeToHeight: 1000,
                    onlyScaleDown: false,
                });
                expect(service.getResizeRatio(2000, 1000, settings)).toBe(0.25);
                expect(service.getResizeRatio(1000, 1000, settings)).toBe(0.5);
                expect(service.getResizeRatio(500, 1000, settings)).toBe(1);
                expect(service.getResizeRatio(250, 1000, settings)).toBe(1);
                expect(service.getResizeRatio(250, 123, settings)).toBe(2);
                expect(service.getResizeRatio(123, 500, settings)).toBe(2);
            });
        });

        describe('when onlyScaleDown is true', () => {
            it('when resizeToWidth is set', () => {
                settings.setOptions({
                    resizeToWidth: 500,
                    resizeToHeight: 0,
                    onlyScaleDown: true,
                });
                expect(service.getResizeRatio(250, 1234, settings)).toBe(1);
                expect(service.getResizeRatio(250, 125, settings)).toBe(1);
                expect(service.getResizeRatio(1000, 125, settings)).toBe(0.5);
            });

            it('when resizeToHeight is set', () => {
                settings.setOptions({
                    resizeToWidth: 0,
                    resizeToHeight: 500,
                    onlyScaleDown: true,
                });
                expect(service.getResizeRatio(1234, 250, settings)).toBe(1);
                expect(service.getResizeRatio(125, 250, settings)).toBe(1);
                expect(service.getResizeRatio(125, 1000, settings)).toBe(0.5);
            });

            it('when resizeToWidth and resizeToHeight is set', () => {
                settings.setOptions({
                    resizeToWidth: 1000,
                    resizeToHeight: 500,
                    onlyScaleDown: true,
                });
                expect(service.getResizeRatio(1000, 250, settings)).toBe(1);
                expect(service.getResizeRatio(2000, 250, settings)).toBe(0.5);
                expect(service.getResizeRatio(100, 250, settings)).toBe(1);

                expect(service.getResizeRatio(250, 500, settings)).toBe(1);
                expect(service.getResizeRatio(250, 1000, settings)).toBe(0.5);
                expect(service.getResizeRatio(250, 50, settings)).toBe(1);
            });
        });
    });
});
