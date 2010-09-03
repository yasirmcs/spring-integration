/*
 * Copyright 2009-2010 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.springframework.integration.monitor;

import java.util.concurrent.atomic.AtomicInteger;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.integration.Message;
import org.springframework.integration.MessageChannel;
import org.springframework.jmx.export.annotation.ManagedMetric;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.jmx.support.MetricType;
import org.springframework.util.StopWatch;

/**
 * Registers all message channels, and accumulates statistics about their performance. The statistics are then published
 * locally for other components to consume and publish remotely.
 * 
 * @author Dave Syer
 * @author Helena Edelson
 */
@ManagedResource
public class SimpleMessageChannelMonitor implements MethodInterceptor, MessageChannelMonitor {

	protected final Log logger = LogFactory.getLog(getClass());

	public static final long ONE_SECOND_SECONDS = 1;

	public static final long ONE_MINUTE_SECONDS = 60;

	public static final int DEFAULT_MOVING_AVERAGE_WINDOW = 10;

	private ExponentialMovingAverageCumulativeHistory sendDuration = new ExponentialMovingAverageCumulativeHistory(
			DEFAULT_MOVING_AVERAGE_WINDOW);

	private final ExponentialMovingAverageRateCumulativeHistory sendErrorRate = new ExponentialMovingAverageRateCumulativeHistory(
			ONE_SECOND_SECONDS, ONE_MINUTE_SECONDS, DEFAULT_MOVING_AVERAGE_WINDOW);

	private final ExponentialMovingAverageRatioCumulativeHistory sendSuccessRatio = new ExponentialMovingAverageRatioCumulativeHistory(
			ONE_MINUTE_SECONDS, DEFAULT_MOVING_AVERAGE_WINDOW);

	private final ExponentialMovingAverageRateCumulativeHistory sendRate = new ExponentialMovingAverageRateCumulativeHistory(
			ONE_SECOND_SECONDS, ONE_MINUTE_SECONDS, DEFAULT_MOVING_AVERAGE_WINDOW);

	private final AtomicInteger sendCount = new AtomicInteger();

	private final AtomicInteger sendErrorCount = new AtomicInteger();

	private final String name;

	public SimpleMessageChannelMonitor(String name) {
		this.name = name;
	}

	public void destroy() {
		if (logger.isDebugEnabled()) {
			logger.debug(sendDuration);
		}
	}

	public String getName() {
		return name;
	}

	public Object invoke(MethodInvocation invocation) throws Throwable {
		String method = invocation.getMethod().getName();
		MessageChannel channel = (MessageChannel) invocation.getThis();
		return doInvoke(invocation, method, channel);
	}

	protected Object doInvoke(MethodInvocation invocation, String method, MessageChannel channel) throws Throwable {
		if ("send".equals(method)) {
			Message<?> message = (Message<?>) invocation.getArguments()[0];
			return monitorSend(invocation, channel, message);
		}
		return invocation.proceed();
	}

	private Object monitorSend(MethodInvocation invocation, MessageChannel channel, Message<?> message)
			throws Throwable {

		if (logger.isTraceEnabled()) {
			logger.trace("Recording send on channel(" + channel + ") : message(" + message + ")");
		}

		final StopWatch timer = new StopWatch(channel + ".send:execution");

		try {
			timer.start();

			sendCount.incrementAndGet();
			sendRate.increment();

			Object result = invocation.proceed();

			timer.stop();
			sendSuccessRatio.success();
			sendDuration.append(timer.getTotalTimeSeconds());
			return result;

		}
		catch (Throwable e) {
			sendErrorCount.incrementAndGet();
			sendSuccessRatio.failure();
			sendErrorRate.increment();
			throw e;
		}
		finally {
			if (logger.isTraceEnabled()) {
				logger.trace(timer);
			}
		}
	}

	@ManagedMetric(metricType = MetricType.COUNTER, displayName = "MessageChannel Sends")
	public int getSendCount() {
		return sendCount.get();
	}

	@ManagedMetric(metricType = MetricType.COUNTER, displayName = "MessageChannel Send Errors")
	public int getSendErrorCount() {
		return sendErrorCount.get();
	}

	@ManagedMetric(metricType = MetricType.GAUGE, displayName = "Channel Time Since Last Send in Seconds")
	public double getTimeSinceLastSend() {
		return sendRate.getTimeSinceLastMeasurement();
	}

	@ManagedMetric(metricType = MetricType.GAUGE, displayName = "Channel Send Rate per Second")
	public double getSendRate() {
		return sendRate.getMean();
	}

	@ManagedMetric(metricType = MetricType.GAUGE, displayName = "Channel Error Rate per Second")
	public double getErrorRate() {
		return sendErrorRate.getMean();
	}

	@ManagedMetric(metricType = MetricType.GAUGE, displayName = "Mean Channel Error Ratio per Minute")
	public double getErrorRatio() {
		return 1 - sendSuccessRatio.getMean();
	}

	@ManagedMetric(metricType = MetricType.GAUGE, displayName = "Channel Send Mean Duration")
	public double getMeanSendDuration() {
		return sendDuration.getMean();
	}

	@ManagedMetric(metricType = MetricType.GAUGE, displayName = "Channel Send Min Duration")
	public double getMinSendDuration() {
		return sendDuration.getMin();
	}

	@ManagedMetric(metricType = MetricType.GAUGE, displayName = "Channel Send Max Duration")
	public double getMaxSendDuration() {
		return sendDuration.getMax();
	}

	@ManagedMetric(metricType = MetricType.GAUGE, displayName = "Channel Send Standard Deviation Duration")
	public double getStandardDeviationSendDuration() {
		return sendDuration.getStandardDeviation();
	}

	@Override
	public String toString() {
		return String.format("MessageChannelMonitor: [name=%s, sends=%d]", name, sendCount.get());
	}
}
