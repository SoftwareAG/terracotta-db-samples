/*
 * Copyright (c) 2020 Software AG, Darmstadt, Germany and/or its licensors
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.PersistentCacheManager;
import org.ehcache.clustered.client.config.builders.ClusteredResourcePoolBuilder;
import org.ehcache.clustered.client.config.builders.ClusteringServiceConfigurationBuilder;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.management.registry.DefaultManagementRegistryConfiguration;
import org.terracotta.connection.ConnectionException;

import java.net.URI;
import java.util.Random;
import java.util.UUID;

public class EhCache3MultiStripe {
  private static final String DEFAULT_TSA_PORT = "9410";
  private static final String TERRACOTTA_URI_ENV = "TERRACOTTA_SERVER_URL";
  private static final String CACHE_MANAGER_ALIAS = "clustered-cache-manager";
  private static final String CACHE_ALIAS = "clustered-cache";
  private static final String SERVER_RESOURCE = "main";
  private static final String SHARED_RESOURCE_POOL = "resource-pool-a";
  private static final String DEFAULT_SERVER_URI_STR = "terracotta://localhost:" + DEFAULT_TSA_PORT;
  private static final String SERVER_URI_STR = System.getenv(TERRACOTTA_URI_ENV) == null ? DEFAULT_SERVER_URI_STR : System.getenv(TERRACOTTA_URI_ENV);

  public static void main(String[] args) throws Exception {
    try (CacheManager cacheManager = createCacheManager()) {
      startCacheOperations(cacheManager);
    }
  }

  private static CacheManager createCacheManager() throws ConnectionException {
    final URI uri = URI.create(SERVER_URI_STR + "/" + CACHE_MANAGER_ALIAS);
    final CacheManagerBuilder<PersistentCacheManager> clusteredCacheManagerBuilder = CacheManagerBuilder
        .newCacheManagerBuilder()
        .with(ClusteringServiceConfigurationBuilder
            .cluster(uri)
            .autoCreate()
            .defaultServerResource(SERVER_RESOURCE)
            .resourcePool(SHARED_RESOURCE_POOL, 10, MemoryUnit.MB))
        .using(new DefaultManagementRegistryConfiguration()
            .addTags("my-client-tag", "another-client-tag")
            .setCacheManagerAlias(CACHE_MANAGER_ALIAS))
        .withCache(CACHE_ALIAS, CacheConfigurationBuilder.newCacheConfigurationBuilder(
            Long.class,
            String.class,
            ResourcePoolsBuilder.newResourcePoolsBuilder()
                .heap(1000, EntryUnit.ENTRIES)
                .offheap(1, MemoryUnit.MB)
                .with(ClusteredResourcePoolBuilder.clusteredShared(SHARED_RESOURCE_POOL))));

    System.out.println("Building the clustered CacheManager, connecting to : " + uri + ", using " + SERVER_RESOURCE);

    return clusteredCacheManagerBuilder.build(true);
  }

  private static void startCacheOperations(CacheManager cacheManager) throws InterruptedException {
    Cache<Long, String> cache = cacheManager.getCache(CACHE_ALIAS, Long.class, String.class);
    Random random = new Random();

    for (int i = 0; i < 10; i++) {
      Long key = random.nextLong();
      String value = UUID.randomUUID().toString();

      System.out.println("" + (i + 1) + ". Putting key : " + key + " with value : " + value);
      cache.put(key, value);
    }
  }
}
