/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.cache.local;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.query.QueryCancelledException;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.configuration.SqlConfiguration;
import org.apache.ignite.internal.util.typedef.X;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.junit.Test;

import static org.apache.ignite.cache.CacheMode.LOCAL;

/**
 * Tests local query default timeouts.
 */
public class IgniteCacheLocalQueryDefaultTimeoutSelfTest extends GridCommonAbstractTest {
    /** Cache size. */
    private static final int CACHE_SIZE = 10_000;

    /** Default query timeout */
    private static final long DEFAULT_QUERY_TIMEOUT = 1000;

    /** */
    private static final String QUERY = "select a._val, b._val from String a, String b";

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(igniteInstanceName);

        CacheConfiguration<Integer, String> ccfg = new CacheConfiguration<>(DEFAULT_CACHE_NAME);
        ccfg.setIndexedTypes(Integer.class, String.class);
        ccfg.setCacheMode(LOCAL);

        cfg.setCacheConfiguration(ccfg);
        cfg.setSqlConfiguration(new SqlConfiguration().setDefaultQueryTimeout(DEFAULT_QUERY_TIMEOUT));

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTestsStarted() throws Exception {
        super.beforeTestsStarted();

        startGrid(0);
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        super.afterTest();

        grid(0).cache(DEFAULT_CACHE_NAME).removeAll();
    }

    /**
     * @param cache Cache.
     */
    private void loadCache(IgniteCache<Integer, String> cache) {
        int p = 1;

        for (int i = 1; i <= CACHE_SIZE; i++) {
            char[] tmp = new char[256];
            Arrays.fill(tmp, ' ');
            cache.put(i, new String(tmp));

            if (i / (float)CACHE_SIZE >= p / 10f) {
                log().info("Loaded " + i + " of " + CACHE_SIZE);

                p++;
            }
        }
    }

    /**
     * Tests query execution with default query timeout.
     */
    @Test
    public void testQueryDefaultTimeout() {
        testQuery(false, 1, TimeUnit.MILLISECONDS);
    }

    /**
     * Tests query execution with query timeout.
     */
    @Test
    public void testQueryTimeout() {
        testQuery(true, 1, TimeUnit.SECONDS);
    }

    /**
     * Tests cancellation.
     */
    private void testQuery(boolean timeout, int timeoutUnits, TimeUnit timeUnit) {
        Ignite ignite = grid(0);

        IgniteCache<Integer, String> cache = ignite.cache(DEFAULT_CACHE_NAME);

        loadCache(cache);

        SqlFieldsQuery qry = new SqlFieldsQuery(QUERY);

        final QueryCursor<List<?>> cursor;
        if (timeout) {
            qry.setTimeout(timeoutUnits, timeUnit);

            cursor = cache.query(qry);
        }
        else {
            cursor = cache.query(qry);

            ignite.scheduler().runLocal(new Runnable() {
                @Override public void run() {
                    cursor.close();
                }
            }, timeoutUnits, timeUnit);
        }

        try (QueryCursor<List<?>> ignored = cursor) {
            cursor.iterator();

            fail("Expecting timeout");
        }
        catch (Exception e) {
            assertNotNull("Must throw correct exception", X.cause(e, QueryCancelledException.class));
        }

        // Test must exit gracefully.
    }
}

