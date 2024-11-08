import { updateHeaderHeight } from 'app/shared/util/navbar.util';

describe('updateHeaderHeight', () => {
    document.querySelector = jest.fn();
    const setPropertyMock = jest.fn();
    document.documentElement.style.setProperty = setPropertyMock;

    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('should update the --header-height variable based on the navbar height', () => {
        const mockNavbar = {
            getBoundingClientRect: jest.fn(() => ({ height: 80 })),
        };
        document.querySelector.mockReturnValue(mockNavbar);
        jest.useFakeTimers();

        updateHeaderHeight();

        jest.runAllTimers();
        expect(document.querySelector).toHaveBeenCalledWith('jhi-navbar');
        expect(mockNavbar.getBoundingClientRect).toHaveBeenCalled();
        expect(setPropertyMock).toHaveBeenCalledWith('--header-height', '80px');
        jest.useRealTimers();
    });

    it('should not update --header-height if navbar is not found', () => {
        document.querySelector.mockReturnValue(null);
        jest.useFakeTimers();

        updateHeaderHeight();

        jest.runAllTimers();
        expect(setPropertyMock).not.toHaveBeenCalled();
        jest.useRealTimers();
    });
});
