package com.writingassistant.jobs;

import java.io.Serializable;
import java.util.Objects;

/**
 * Tracks worker lease ownership and heartbeat status to prevent duplicate execution
 * and allow safe detection of dead or abandoned workers.
 */
public record JobLease(
        String leaseOwner,
        long acquiredAtMillis,
        long lastHeartbeatMillis,
        long leaseDurationMillis
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final long DEFAULT_LEASE_DURATION_MILLIS = 30_000L; // 30 seconds
    public static final long DEFAULT_HEARTBEAT_INTERVAL_MILLIS = 10_000L; // 10 seconds

    public JobLease {
        Objects.requireNonNull(leaseOwner, "leaseOwner must not be null");
        if (leaseDurationMillis <= 0) leaseDurationMillis = DEFAULT_LEASE_DURATION_MILLIS;
    }

    public static JobLease acquire(String leaseOwner, long leaseDurationMillis) {
        long now = System.currentTimeMillis();
        return new JobLease(leaseOwner, now, now, leaseDurationMillis);
    }

    public JobLease renew(long nowMillis) {
        return new JobLease(leaseOwner, acquiredAtMillis, nowMillis, leaseDurationMillis);
    }

    public boolean isExpired(long nowMillis) {
        return (nowMillis - lastHeartbeatMillis) > leaseDurationMillis;
    }
}
