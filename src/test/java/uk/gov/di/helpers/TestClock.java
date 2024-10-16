package uk.gov.di.helpers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public class TestClock extends Clock {
    private Instant instant;
    private final ZoneId zone;

    public TestClock(Instant instant, ZoneId zoneId) {
        this.instant = instant;
        this.zone = zoneId;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        if (zone.equals(this.zone)) { // intentional NPE
            return this;
        }
        return new TestClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return this.instant;
    }

    public void setInstant(Instant instant) {
        this.instant = instant;
    }
}
