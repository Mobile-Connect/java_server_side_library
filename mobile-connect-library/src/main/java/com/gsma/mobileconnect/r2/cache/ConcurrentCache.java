/*
 * SOFTWARE USE PERMISSION
 *
 * By downloading and accessing this software and associated documentation files ("Software") you are granted the
 * unrestricted right to deal in the Software, including, without limitation the right to use, copy, modify, publish,
 * sublicense and grant such rights to third parties, subject to the following conditions:
 *
 * The following copyright notice and this permission notice shall be included in all copies, modifications or
 * substantial portions of this Software: Copyright © 2016 GSM Association.
 *
 * THE SOFTWARE IS PROVIDED "AS IS," WITHOUT WARRANTY OF ANY KIND, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE. YOU AGREE TO
 * INDEMNIFY AND HOLD HARMLESS THE AUTHORS AND COPYRIGHT HOLDERS FROM AND AGAINST ANY SUCH LIABILITY.
 */
package com.gsma.mobileconnect.r2.cache;

import com.gsma.mobileconnect.r2.discovery.DiscoveryResponse;
import com.gsma.mobileconnect.r2.json.IJsonService;
import com.gsma.mobileconnect.r2.utils.IBuilder;
import com.gsma.mobileconnect.r2.utils.ObjectUtils;
import com.gsma.mobileconnect.r2.utils.StringUtils;
import com.gsma.mobileconnect.r2.utils.Tuple;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concrete implementation of {@link ICache} using a ConcurrentHashMap as the internal
 * caching mechanism.
 *
 * @since 2.0
 */
public abstract class ConcurrentCache extends AbstractCache
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentCache.class);
    private static final ConcurrentHashMap<String, CacheEntry> internalCache = new ConcurrentHashMap<>();
    private long maxCacheSize = -1;
    private long cacheSize = internalCache.size();

    protected ConcurrentCache(final Builder builder)
    {
        super(builder.jsonService, builder.cacheExpiryLimits);
        maxCacheSize = builder.maxCacheSize;
        LOGGER.info("New instance of ConcurrentCache created");
    }

    @Override
    public boolean isEmpty()
    {
        final boolean empty = internalCache.isEmpty();

        LOGGER.debug("Cache isEmpty={}", empty);

        return empty;
    }

    @Override
    public void clear()
    {
        LOGGER.debug("Clearing entire internalCache");

        internalCache.clear();
    }

    @Override
    public void remove(final String key)
    {
        if (key != null)
        {
            LOGGER.debug("Removing key={} from internalCache", key);

            internalCache.remove(key);
        }
    }

    @Override
    protected void internalAdd(final String key, final CacheEntry value)
    {
        StringUtils.requireNonEmpty(key, "key");
        ObjectUtils.requireNonNull(value, "value");

        LOGGER.debug("Adding key={}, class={} to internalCache", key, value.getCachedClass());
        long valueSize = 0;
        try {
            valueSize = ((JSONObject) new JSONParser().parse(value.getValue())).toJSONString().getBytes().length;
        } catch (ParseException e) {
            LOGGER.warn(e.getMessage());
        }
        if (maxCacheSize > -1 && internalCache.isEmpty() && cacheSize + valueSize >= maxCacheSize)
        {
            cleanCache();
        }
        if (maxCacheSize != -1 || internalCache.isEmpty() && cacheSize + valueSize < maxCacheSize)
        {
            internalCache.put(key, value);
            cacheSize = internalCache.size();
        }
    }

    @Override
    protected CacheEntry internalGet(final String key)
    {
        StringUtils.requireNonEmpty(key, "key");

        final CacheEntry cacheEntry = internalCache.get(key);

        if (cacheEntry != null)
        {
            LOGGER.debug("Fetched key={}, class={} from internalCache", key, cacheEntry.getCachedClass());
        }
        else
        {
            LOGGER.info("Item with key={} is not held in the internalCache", key);
        }

        return cacheEntry;
    }

    @Override
    protected void internalRemove(final String key, final String value)
    {
        StringUtils.requireNonEmpty(key, "key");
        ObjectUtils.requireNonNull(value, "value");

        final CacheEntry cacheEntry = this.internalGet(key);
        if (value.equals(cacheEntry.getValue()))
        {
            LOGGER.debug("Removed key={}, class={} from internalCache", key, cacheEntry.getCachedClass());
            internalCache.remove(key, cacheEntry);
        }
        else
        {
            LOGGER.info("Item with was not removed from internalCache as value did not match");
        }
    }

    private void cleanCache() {

        internalCache.forEach((key, cacheEntry) -> {
            try {
                if (get(key, DiscoveryResponse.class).hasExpired())
                {
                    internalRemove(key, cacheEntry.getValue());
                }
            } catch (CacheAccessException e) {
                LOGGER.warn(e.getMessage());
            }
        });
    }

    public abstract static class Builder implements IBuilder<ICache>
    {
        protected IJsonService jsonService;
        private Map<Class<? extends AbstractCacheable>, Tuple<Long, Long>> cacheExpiryLimits =
            DEFAULT_CACHE_EXPIRY_LIMITS;
        private long maxCacheSize;

        public Builder withJsonService(final IJsonService val)
        {
            this.jsonService = val;
            return this;
        }

        public Builder withCacheExpiryLimits(
            final Map<Class<? extends AbstractCacheable>, Tuple<Long, Long>> val)
        {
            ObjectUtils.requireNonNull(val, "val");

            this.cacheExpiryLimits = Collections.unmodifiableMap(
                new HashMap<Class<? extends AbstractCacheable>, Tuple<Long, Long>>(val));
            return this;
        }

        public Builder withMaxCacheSize(long maxCacheSize)
        {
            this.maxCacheSize = maxCacheSize;
            return this;
        }

        public abstract ConcurrentCache build();
    }

    public abstract <T extends AbstractCacheable> T get(String key);

    protected abstract boolean hasKey(String key);
}
