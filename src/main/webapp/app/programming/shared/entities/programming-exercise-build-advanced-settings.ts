import dayjs from 'dayjs/esm';

/**
 * Optional time window in which pushes to student repositories are built.
 * Either bound may be missing, both bounds are inclusive. Mirrors the server-side record BuildTimeWindow.
 */
export interface BuildTimeWindow {
    start?: dayjs.Dayjs;
    end?: dayjs.Dayjs;
}

/**
 * Evolvable advanced settings of a build config. Mirrors the server-side record BuildConfigAdvancedSettingsDTO.
 * The branching settings are not part of it, they stay flat on the build config.
 */
export interface BuildConfigAdvancedSettings {
    buildTimeWindow?: BuildTimeWindow;
}
