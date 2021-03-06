/*
 * Copyright 2016 the original author or authors.
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
package org.glowroot.agent.impl;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.agent.plugin.api.AuxThreadContext;
import org.glowroot.agent.plugin.api.MessageSupplier;
import org.glowroot.agent.plugin.api.TraceEntry;
import org.glowroot.agent.plugin.api.internal.NopTransactionService.NopTraceEntry;
import org.glowroot.agent.plugin.api.util.FastThreadLocal.Holder;

import static org.glowroot.agent.util.Checkers.castInitialized;

// TODO this has ability to retain a transaction beyond its completion
// but don't use WeakReference because it needs to be able to retain transaction in async case
// (at least until transaction completion)
// so ideally clear references here at transaction completion
class AuxThreadContextImpl implements AuxThreadContext {

    private static final Logger logger = LoggerFactory.getLogger(AuxThreadContextImpl.class);

    private final Transaction transaction;
    // null when parent is a limit exceeded auxiliary thread context, to prevent retaining parent
    private final @Nullable TraceEntryImpl parentTraceEntry;
    // null when parent is a limit exceeded auxiliary thread context, to prevent retaining parent
    private final @Nullable TraceEntryImpl parentThreadContextPriorEntry;
    private final @Nullable MessageSupplier servletMessageSupplier;
    private final TransactionRegistry transactionRegistry;
    private final TransactionServiceImpl transactionService;

    AuxThreadContextImpl(Transaction transaction, @Nullable TraceEntryImpl parentTraceEntry,
            @Nullable TraceEntryImpl parentThreadContextPriorEntry,
            @Nullable MessageSupplier servletMessageSupplier,
            TransactionRegistry transactionRegistry, TransactionServiceImpl transactionService) {
        this.transaction = transaction;
        this.parentTraceEntry = parentTraceEntry;
        this.parentThreadContextPriorEntry = parentThreadContextPriorEntry;
        this.servletMessageSupplier = servletMessageSupplier;
        this.transactionRegistry = transactionRegistry;
        this.transactionService = transactionService;
        if (logger.isDebugEnabled() && parentTraceEntry != null) {
            logger.debug("new AUX thread context: {}, parent thread context: {}, thread name: {}",
                    castInitialized(this).hashCode(),
                    parentTraceEntry.getThreadContext().hashCode(),
                    Thread.currentThread().getName(), new Exception());
        }
    }

    @Override
    public TraceEntry start() {
        return start(false);
    }

    @Override
    public TraceEntry startAndMarkAsyncTransactionComplete() {
        return start(true);
    }

    private TraceEntry start(boolean completeAsyncTransaction) {
        Holder</*@Nullable*/ ThreadContextImpl> threadContextHolder =
                transactionRegistry.getCurrentThreadContextHolder();
        ThreadContextImpl context = threadContextHolder.get();
        if (context != null) {
            if (completeAsyncTransaction) {
                context.completeAsyncTransaction();
            }
            return NopTraceEntry.INSTANCE;
        }
        context = transactionService.startAuxThreadContextInternal(transaction, parentTraceEntry,
                parentThreadContextPriorEntry, servletMessageSupplier, threadContextHolder);
        if (context == null) {
            // transaction is already complete or auxiliary thread context limit exceeded
            return NopTraceEntry.INSTANCE;
        }
        if (logger.isDebugEnabled() && parentTraceEntry != null) {
            logger.debug("start AUX thread context: {}, thread context: {},"
                    + " parent thread context: {}, thread name: {}", hashCode(), context.hashCode(),
                    parentTraceEntry.getThreadContext().hashCode(),
                    Thread.currentThread().getName(), new Exception());
        }
        if (completeAsyncTransaction) {
            context.completeAsyncTransaction();
        }
        return context.getRootEntry();
    }
}
