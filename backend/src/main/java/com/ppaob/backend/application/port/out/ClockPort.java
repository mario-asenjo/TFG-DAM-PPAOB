package com.ppaob.backend.application.port.out;

import java.time.Instant;

/**
 * Outbound time source port for the application layer.
 *
 * <p>Use cases depend on this abstraction instead of calling system time directly
 * so time-dependent behavior can be deterministic in tests.</p>
 */
public interface ClockPort {
    /**
     * Returns the current wall-clock instant.
     *
     * @return current instant supplied by the configured clock adapter
     */
    Instant now();
}
