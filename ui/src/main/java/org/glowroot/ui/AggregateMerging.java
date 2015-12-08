/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.ui;

import java.util.List;

import com.google.common.collect.Lists;
import org.immutables.value.Value;

import org.glowroot.common.util.NotAvailableAware;
import org.glowroot.common.util.Styles;
import org.glowroot.storage.repo.AggregateRepository.OverviewAggregate;
import org.glowroot.storage.repo.MutableTimer;
import org.glowroot.wire.api.model.AggregateOuterClass.Aggregate;
import org.glowroot.wire.api.model.AggregateOuterClass.Aggregate.Timer;

class AggregateMerging {

    private AggregateMerging() {}

    static TimerMergedAggregate getTimerMergedAggregate(List<OverviewAggregate> overviewAggregates)
            throws Exception {
        long transactionCount = 0;
        List<MutableTimer> rootTimers = Lists.newArrayList();
        for (OverviewAggregate aggregate : overviewAggregates) {
            transactionCount += aggregate.transactionCount();
            mergeRootTimers(aggregate.rootTimers(), rootTimers);
        }
        ImmutableTimerMergedAggregate.Builder timerMergedAggregate =
                ImmutableTimerMergedAggregate.builder();
        timerMergedAggregate.rootTimers(rootTimers);
        timerMergedAggregate.transactionCount(transactionCount);
        return timerMergedAggregate.build();
    }

    static ThreadInfoAggregate getThreadInfoAggregate(List<OverviewAggregate> overviewAggregates) {
        double totalCpuNanos = NotAvailableAware.NA;
        double totalBlockedNanos = NotAvailableAware.NA;
        double totalWaitedNanos = NotAvailableAware.NA;
        double totalAllocatedBytes = NotAvailableAware.NA;
        for (OverviewAggregate overviewAggregate : overviewAggregates) {
            totalCpuNanos = NotAvailableAware.add(totalCpuNanos, overviewAggregate.totalCpuNanos());
            totalBlockedNanos =
                    NotAvailableAware.add(totalBlockedNanos, overviewAggregate.totalBlockedNanos());
            totalWaitedNanos =
                    NotAvailableAware.add(totalWaitedNanos, overviewAggregate.totalWaitedNanos());
            totalAllocatedBytes = NotAvailableAware.add(totalAllocatedBytes,
                    overviewAggregate.totalAllocatedBytes());
        }
        return ImmutableThreadInfoAggregate.builder()
                .totalCpuNanos(totalCpuNanos)
                .totalBlockedNanos(totalBlockedNanos)
                .totalWaitedNanos(totalWaitedNanos)
                .totalAllocatedBytes(totalAllocatedBytes)
                .build();
    }

    private static void mergeRootTimers(List<Aggregate.Timer> toBeMergedRootTimers,
            List<MutableTimer> rootTimers) {
        for (Aggregate.Timer toBeMergedRootTimer : toBeMergedRootTimers) {
            mergeRootTimer(toBeMergedRootTimer, rootTimers);
        }
    }

    private static void mergeRootTimer(Timer toBeMergedRootTimer, List<MutableTimer> rootTimers) {
        for (MutableTimer rootTimer : rootTimers) {
            if (toBeMergedRootTimer.getName().equals(rootTimer.getName())) {
                rootTimer.merge(toBeMergedRootTimer);
                return;
            }
        }
        MutableTimer rootTimer = MutableTimer.createRootTimer(toBeMergedRootTimer.getName(),
                toBeMergedRootTimer.getExtended());
        rootTimer.merge(toBeMergedRootTimer);
        rootTimers.add(rootTimer);
    }

    @Value.Immutable
    interface TimerMergedAggregate {
        long transactionCount();
        List<MutableTimer> rootTimers();
    }

    @Value.Immutable
    @Styles.AllParameters
    interface PercentileValue {
        String dataSeriesName();
        long value();
    }

    @Value.Immutable
    abstract static class ThreadInfoAggregate {

        // aggregates use double instead of long to avoid (unlikely) 292 year nanosecond rollover
        abstract double totalCpuNanos(); // -1 means N/A
        abstract double totalBlockedNanos(); // -1 means N/A
        abstract double totalWaitedNanos(); // -1 means N/A
        abstract double totalAllocatedBytes(); // -1 means N/A

        boolean isEmpty() {
            return NotAvailableAware.isNA(totalCpuNanos())
                    && NotAvailableAware.isNA(totalBlockedNanos())
                    && NotAvailableAware.isNA(totalWaitedNanos())
                    && NotAvailableAware.isNA(totalAllocatedBytes());
        }
    }
}
