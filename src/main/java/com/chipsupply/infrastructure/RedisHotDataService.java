package com.chipsupply.infrastructure;

import com.chipsupply.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Redis cache-aside 与热点物料互斥。Redis 不可用时自动退化到数据库。 */
@Service
@RequiredArgsConstructor
public class RedisHotDataService {
    private static final Logger log = LoggerFactory.getLogger(RedisHotDataService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(2);
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end", Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public <T> T getOrLoad(String namespace, Object id, Class<T> type, Supplier<T> loader) {
        String key = key(namespace, id);
        try {
            String json = redis.opsForValue().get(key);
            if (json != null) return objectMapper.readValue(json, type);
        } catch (Exception exception) {
            log.debug("Redis 缓存读取失败，退化到数据库: {}", key);
        }
        T value = loader.get();
        if (value != null) {
            try {
                redis.opsForValue().set(key, objectMapper.writeValueAsString(value), CACHE_TTL);
            } catch (Exception exception) {
                log.debug("Redis 缓存写入失败，不影响主流程: {}", key);
            }
        }
        return value;
    }

    public void evict(String namespace, Object id) {
        try { redis.delete(key(namespace, id)); }
        catch (Exception exception) { log.debug("Redis 缓存清理失败，不影响主流程"); }
    }

    public boolean withInventoryLock(Long partId, Supplier<Boolean> operation) {
        String lockKey = "chipsupply:lock:inventory:" + partId;
        String token = UUID.randomUUID().toString();
        boolean redisAvailable = true;
        boolean acquired = false;
        try {
            acquired = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, token, LOCK_TTL));
        } catch (Exception exception) {
            redisAvailable = false;
            log.warn("Redis 热点锁不可用，使用数据库原子条件更新兜底");
        }
        if (redisAvailable && !acquired) throw new BusinessException("该物料正在处理，请稍后重试");
        try {
            return operation.get();
        } finally {
            if (acquired) {
                try { redis.execute(UNLOCK, List.of(lockKey), token); }
                catch (Exception exception) { log.debug("热点锁将由 TTL 自动释放: {}", lockKey); }
            }
        }
    }

    private String key(String namespace, Object id) {
        return "chipsupply:cache:" + namespace + ":" + id;
    }
}
