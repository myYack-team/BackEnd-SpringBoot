package com.myyak.service.authService.store;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Component
@ConditionalOnProperty(name = "auth.refresh-store.mode", havingValue = "redis")
public class RedisRefreshTokenSessionStore implements RefreshTokenSessionStore {

    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = script("""
            local current = redis.call('GET', KEYS[1])
            local usedFamily = redis.call('GET', KEYS[2])
            if usedFamily then
              if current and string.sub(current, 66) == usedFamily then
                redis.call('DEL', KEYS[1])
                return 2
              end
              return 0
            end
            if current ~= ARGV[1] .. ':' .. ARGV[3] then return 0 end
            redis.call('SET', KEYS[2], ARGV[3], 'PX', ARGV[5])
            redis.call('SET', KEYS[1], ARGV[2] .. ':' .. ARGV[3], 'PX', ARGV[4])
            return 1
            """);

    private final StringRedisTemplate redis;
    private final String prefix;
    private final byte[] hashKey;

    public RedisRefreshTokenSessionStore(StringRedisTemplate redis,
            @Value("${auth.refresh-store.key-prefix:}") String prefix,
            @Value("${auth.refresh-store.hash-key:}") String hashKey) {
        if (prefix.isBlank() || hashKey.length() < 32) {
            throw new IllegalArgumentException("Redis refresh store requires a key prefix and a dedicated 32+ character hash key");
        }
        this.redis = redis;
        this.prefix = prefix.replaceAll(":+$", "");
        this.hashKey = hashKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void issue(Long userId, String token, String familyId, Instant expiresAt) {
        try {
            redis.opsForValue().set(sessionKey(userId), digest(token) + ":" + familyId,
                    Duration.ofMillis(remainingMillis(expiresAt)));
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    @Override
    public String currentFamily(Long userId) {
        try {
            String value = redis.opsForValue().get(sessionKey(userId));
            if (value == null) return null;
            if (value.length() <= 65 || value.charAt(64) != ':') {
                throw new GeneralException(ErrorStatus.AUTH_TEMPORARY_STORE_UNAVAILABLE);
            }
            return value.substring(65);
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    @Override
    public void rotate(Long userId, String previousToken, String nextToken, String familyId,
                       Instant previousExpiresAt, Instant nextExpiresAt) {
        String oldDigest = digest(previousToken);
        String newDigest = digest(nextToken);
        try {
            Long outcome = redis.execute(ROTATE_SCRIPT,
                    List.of(sessionKey(userId), usedKey(userId, oldDigest)),
                    oldDigest, newDigest, familyId,
                    String.valueOf(remainingMillis(nextExpiresAt)),
                    String.valueOf(remainingMillis(previousExpiresAt)));
            if (outcome != null && outcome == 1L) return;
            if (outcome != null && outcome == 2L) {
                throw new GeneralException(ErrorStatus.AUTH_REFRESH_TOKEN_REUSED);
            }
            throw new GeneralException(ErrorStatus.AUTH_INVALID_REFRESH_TOKEN);
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    @Override
    public void revoke(Long userId) {
        try {
            redis.delete(sessionKey(userId));
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    private String sessionKey(Long userId) { return prefix + ":session:{" + userId + "}"; }
    private String usedKey(Long userId, String digest) { return prefix + ":used:{" + userId + "}:" + digest; }

    private String digest(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Refresh token digest unavailable", e);
        }
    }

    private static long remainingMillis(Instant expiry) {
        long millis = Duration.between(Instant.now(), expiry).toMillis();
        if (millis <= 0) throw new GeneralException(ErrorStatus.AUTH_EXPIRED_TOKEN);
        return millis;
    }

    private static GeneralException unavailable(DataAccessException e) {
        return new GeneralException(ErrorStatus.AUTH_TEMPORARY_STORE_UNAVAILABLE, e);
    }

    private static DefaultRedisScript<Long> script(String source) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(source);
        script.setResultType(Long.class);
        return script;
    }
}
