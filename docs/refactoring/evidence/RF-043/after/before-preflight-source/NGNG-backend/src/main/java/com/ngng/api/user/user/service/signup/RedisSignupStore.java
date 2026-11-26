package com.ngng.api.user.user.service.signup;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class RedisSignupStore implements SignupStore {
    private final StringRedisTemplate redis;
    public RedisSignupStore(StringRedisTemplate redis) { this.redis = redis; }

    // Fixed-size digests are compared without an early exit on the differing byte.
    private static final String COMMON = """
        local function equal(a,b)
          if not a or not b or #a ~= 64 or #b ~= 64 then return false end
          local difference = 0
          for i=1,64 do difference=bit.bor(difference,bit.bxor(string.byte(a,i),string.byte(b,i))) end
          return difference == 0
        end
        local function kind(k,t)
          local v=redis.call('TYPE',k).ok
          return v == 'none' or v == t
        end
        local t=redis.call('TIME'); local now=tonumber(t[1])*1000+math.floor(tonumber(t[2])/1000)
        """;
    private static final String AUTH = """
        if not kind(KEYS[1],'hash') then return {'UNAVAILABLE'} end
        if not equal(redis.call('HGET',KEYS[1],'secret'),ARGV[1]) then return {'REQUIRED'} end
        local expiry=tonumber(redis.call('HGET',KEYS[1],'expiry') or '0')
        if now >= expiry then return {'REQUIRED'} end
        if redis.call('HGET',KEYS[1],'state') == 'consumed' then return {'RESTART'} end
        """;
    private static final Map<String, String> SCRIPTS = Map.of(
        "create", COMMON + """
            if redis.call('EXISTS',KEYS[1]) == 1 then return {'COLLISION'} end
            local expiry=now+1800000
            redis.call('HSETNX',KEYS[1],'secret',ARGV[1])
            redis.call('HSET',KEYS[1],'expiry',expiry,'state','open')
            redis.call('PEXPIREAT',KEYS[1],expiry)
            return {'OK',tostring(expiry)}
            """,
        "issue", COMMON + AUTH + """
            if not kind(KEYS[2],'zset') then return {'UNAVAILABLE'} end
            local c=ARGV[2]; local target=redis.call('HGET',KEYS[1],c..':target')
            if target and not equal(target,ARGV[3]) then return {'REQUIRED'} end
            if tonumber(redis.call('HGET',KEYS[1],c..':checks') or '0') >= 5 then return {'LIMIT','0'} end
            if redis.call('EXISTS',KEYS[3]) == 1 or redis.call('ZSCORE',KEYS[2],ARGV[6]) then return {'COLLISION'} end
            local recent=redis.call('ZRANGEBYSCORE',KEYS[2],'('..tostring(now-600000),'+inf','WITHSCORES')
            if #recent >= 6 then return {'LIMIT',tostring(math.max(1,math.ceil((tonumber(recent[2])+600000-now)/1000)))} end
            local codeExpiry=math.min(now+300000,expiry)
            redis.call('SET',KEYS[3],'reserved','NX','PX',expiry-now)
            redis.call('ZREMRANGEBYSCORE',KEYS[2],'-inf',now-600000)
            redis.call('ZADD',KEYS[2],'NX',now,ARGV[6]); redis.call('PEXPIRE',KEYS[2],600000)
            redis.call('HSET',KEYS[1],c..':target',ARGV[3],c..':generation',ARGV[4],c..':code',ARGV[5],c..':codeExpiry',codeExpiry,c..':state','pending')
            redis.call('HDEL',KEYS[1],c..':proof',c..':proofExpiry')
            return {'OK',tostring(codeExpiry)}
            """,
        "complete", COMMON + AUTH + """
            local c=ARGV[2]
            if redis.call('HGET',KEYS[1],c..':generation') ~= ARGV[3] or redis.call('HGET',KEYS[1],c..':state') ~= 'pending' then return {'RESTART'} end
            if ARGV[4] == 'sent' and now >= tonumber(redis.call('HGET',KEYS[1],c..':codeExpiry') or '0') then return {'REQUIRED'} end
            local state='failed'
            if ARGV[4] == 'sent' and now < tonumber(redis.call('HGET',KEYS[1],c..':codeExpiry') or '0') then state='sent' end
            redis.call('HSET',KEYS[1],c..':state',state)
            if state ~= 'sent' then redis.call('HDEL',KEYS[1],c..':code') end
            return {'OK'}
            """,
        "verify", COMMON + AUTH + """
            local c=ARGV[2]; local state=redis.call('HGET',KEYS[1],c..':state')
            if state == 'verified' then return {'RESTART'} end
            if redis.call('HGET',KEYS[1],c..':generation') ~= ARGV[3] or state ~= 'sent' then return {'REQUIRED'} end
            if now >= tonumber(redis.call('HGET',KEYS[1],c..':codeExpiry') or '0') then return {'REQUIRED'} end
            local checks=tonumber(redis.call('HGET',KEYS[1],c..':checks') or '0')
            if checks >= 5 then return {'LIMIT','0'} end
            local matches=equal(redis.call('HGET',KEYS[1],c..':code'),ARGV[4])
            redis.call('HSET',KEYS[1],c..':checks',checks+1)
            if not matches then
              if checks+1 >= 5 then return {'LIMIT','0'} end
              return {'REQUIRED'}
            end
            local proofExpiry=math.min(now+300000,expiry)
            redis.call('HSET',KEYS[1],c..':proof',ARGV[5],c..':proofExpiry',proofExpiry,c..':state','verified')
            redis.call('HDEL',KEYS[1],c..':code')
            return {'OK',tostring(proofExpiry)}
            """,
        "consume", COMMON + AUTH + """
            for i=1,2 do
              local c=({'email','phone'})[i]; local offset=2+(i-1)*2
              if redis.call('HGET',KEYS[1],c..':state') ~= 'verified'
                or not equal(redis.call('HGET',KEYS[1],c..':target'),ARGV[offset])
                or not equal(redis.call('HGET',KEYS[1],c..':proof'),ARGV[offset+1])
                or now >= tonumber(redis.call('HGET',KEYS[1],c..':proofExpiry') or '0') then return {'REQUIRED'} end
            end
            redis.call('HSET',KEYS[1],'state','consumed','email:state','consumed','phone:state','consumed')
            redis.call('HDEL',KEYS[1],'email:proof','phone:proof','email:code','phone:code')
            return {'OK'}
            """
    );
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<String> run(String operation, List<String> keys, String... arguments) {
        try {
            String script = SCRIPTS.get(operation);
            if (script == null) throw SignupFailure.unavailable();
            List<?> result = redis.execute(new DefaultRedisScript<>(script, List.class), keys, (Object[]) arguments);
            if (result == null || result.isEmpty()) throw SignupFailure.unavailable();
            return result.stream().map(Object::toString).toList();
        } catch (SignupFailure e) { throw e; }
        catch (RuntimeException e) { throw SignupFailure.unavailable(); }
    }
}
