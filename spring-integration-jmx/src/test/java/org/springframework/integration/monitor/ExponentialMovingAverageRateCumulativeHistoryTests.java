/*
 * Copyright 2002-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.integration.monitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * @author Dave Syer
 * 
 */
public class ExponentialMovingAverageRateCumulativeHistoryTests {

	private ExponentialMovingAverageRateCumulativeHistory history = new ExponentialMovingAverageRateCumulativeHistory(
			1., 10., 10);

	@Test
	public void testGetCount() {
		assertEquals(0, history.getCount());
		history.increment();
		assertEquals(1, history.getCount());
	}

	@Test
	public void testGetTimeSinceLastMeasurement() throws Exception {
		history.increment();
		Thread.sleep(20L);
		assertTrue(history.getTimeSinceLastMeasurement() > 0);
	}

	@Test
	public void testGetEarlyMean() throws Exception {
		assertEquals(0, history.getMean(), 0.01);
		Thread.sleep(20L);
		history.increment();
		assertEquals(50, history.getMean(), 10);
	}

	@Test
	public void testGetMean() throws Exception {
		assertEquals(0, history.getMean(), 0.01);
		Thread.sleep(20L);
		history.increment();
		Thread.sleep(20L);
		history.increment();
		assertEquals(50, history.getMean(), 10);
		Thread.sleep(20L);
		assertEquals(35, history.getMean(), 10);
	}

	@Test
	public void testGetStandardDeviation() throws Exception {
		assertEquals(0, history.getStandardDeviation(), 0.01);
		Thread.sleep(20L);
		history.increment();
		Thread.sleep(22L);
		history.increment();
		Thread.sleep(18L);
		assertEquals(1.5, history.getStandardDeviation(), 1);
	}

}
