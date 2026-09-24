package edu.cit.pescante.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's @Scheduled task infrastructure.
 * Required for PendingOrderScheduler and OrderTrackingScheduler.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
