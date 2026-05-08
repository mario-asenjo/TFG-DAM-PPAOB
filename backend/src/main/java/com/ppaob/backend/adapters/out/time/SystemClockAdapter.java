package com.ppaob.backend.adapters.out.time;

import com.ppaob.backend.application.port.out.ClockPort;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Production clock adapter backed by {@link Instant#now()}.
 */
@Component
public class SystemClockAdapter implements ClockPort {
    @Override
    /**
     * Returns the current wall-clock instant.
     *
     * @return current UTC instant from the running JVM
     */
    public Instant now() {
        return Instant.now();
    }
}
