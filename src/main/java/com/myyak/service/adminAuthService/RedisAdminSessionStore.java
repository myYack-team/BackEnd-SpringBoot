package com.myyak.service.adminAuthService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

@Component
public class RedisAdminSessionStore implements AdminSessionStore {
    private final StringRedisTemplate redis;
    private final String prefix;
    // GET/SET/DEL/EVAL only; existing auth namespace and string commands are reused.
    private static final DefaultRedisScript<Long> ISSUE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[2]) then return 0 end
            local used = tonumber(redis.call('GET', KEYS[3]) or '0')
            if used >= 10 then return 0 end
            redis.call('SET', KEYS[2], '1', 'EX', 60)
            if used == 0 then redis.call('SET', KEYS[3], '1', 'EX', 3600)
            else redis.call('SET', KEYS[3], tostring(used + 1), 'KEEPTTL') end
            redis.call('SET', KEYS[1], ARGV[1] .. ':0', 'EX', ARGV[2])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if not value then return 0 end
            local digest, attempts = string.match(value, '^([^:]+):(%d+)$')
            if not digest then redis.call('DEL', KEYS[1]); return 0 end
            if digest == ARGV[1] then redis.call('DEL', KEYS[1]); return 1 end
            attempts = tonumber(attempts) + 1
            if attempts >= 5 then redis.call('DEL', KEYS[1])
            else redis.call('SET', KEYS[1], digest .. ':' .. attempts, 'KEEPTTL') end
            return 0
            """, Long.class);

    public RedisAdminSessionStore(StringRedisTemplate redis,
            @Value("${auth.temporary-store.key-prefix:myyak:prod:auth:v1}") String prefix) {
        this.redis = redis;
        this.prefix = prefix.replaceAll(":+$", "") + ":admin:";
    }

    @Override
    public boolean createChallenge(String challenge, String codeHash, Duration ttl) {
        return execute(() -> Long.valueOf(1).equals(redis.execute(ISSUE,
                List.of(prefix + "challenge:" + challenge, prefix + "cooldown", prefix + "hourly"),
                codeHash, Long.toString(ttl.toSeconds()))));
    }

    @Override
    public boolean consumeChallenge(String challenge, String codeHash) {
        return execute(() -> Long.valueOf(1).equals(redis.execute(CONSUME,
                List.of(prefix + "challenge:" + challenge), codeHash)));
    }

    @Override
    public void createSession(String sessionHash, String csrfToken, Duration ttl) {
        execute(() -> { redis.opsForValue().set(prefix + "session:" + sessionHash, csrfToken, ttl); return true; });
    }

    @Override
    public String findSession(String sessionHash) {
        return execute(() -> redis.opsForValue().get(prefix + "session:" + sessionHash));
    }

    @Override
    public void deleteSession(String sessionHash) {
        execute(() -> redis.delete(prefix + "session:" + sessionHash));
    }

    private <T> T execute(Supplier<T> operation) {
        try { return operation.get(); }
        catch (DataAccessException e) { throw new GeneralException(ErrorStatus.ADMIN_UNAVAILABLE); }
    }
}
