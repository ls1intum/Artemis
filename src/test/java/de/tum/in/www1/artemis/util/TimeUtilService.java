package de.tum.in.www1.artemis.util;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

@Service
public class TimeUtilService {

    public long nanoSecondsToSeconds(long nanoSeconds) {
        return TimeUnit.SECONDS.convert(Duration.ofSeconds(nanoSeconds));
    }

    public long secondsToNanoSeconds(long seconds) {
        return TimeUnit.NANOSECONDS.convert(Duration.ofSeconds(seconds));
    }

    public long nanoSecondsToMilliSeconds(long nanoSeconds) {
        return TimeUnit.MILLISECONDS.convert(Duration.ofNanos(nanoSeconds));
    }

    public long milliSecondsToNanoSeconds(long millSeconds) {
        return TimeUnit.NANOSECONDS.convert(Duration.ofMillis(millSeconds));
    }
}
