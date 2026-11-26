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
        local verificationTtlMs=300000
        local function digest(v)
          return type(v) == 'string' and #v == 64 and string.match(v,'^[0-9a-f]+$') ~= nil
        end
        local function token(v)
          return type(v) == 'string' and #v == 43 and string.match(v,'^[A-Za-z0-9_-]+$') ~= nil
        end
        local function channel(v) return v == 'email' or v == 'phone' end
        local function keyParts(k)
          if type(k) ~= 'string' then return nil end
          local environment,epoch,c,kind,d=string.match(k,'^([a-z]+):{signup}:([A-Za-z0-9_-]+):signup:([a-z]+):([a-z]+):([0-9a-f]+)$')
          local environments={['local']=true,development=true,test=true,staging=true,production=true}
          if not environment or not environments[environment] or #epoch < 16 or #epoch > 64 or not digest(d) then return nil end
          return environment..':'..epoch,c,kind
        end
        local function requestKeys(c)
          local namespace,ac,ak=keyParts(KEYS[1])
          if not namespace or ac ~= 'both' or ak ~= 'attempt' then return false end
          if #KEYS == 1 then return true end
          local qn,qc,qk=keyParts(KEYS[2]); local gn,gc,gk=keyParts(KEYS[3])
          return qn == namespace and gn == namespace and qc == c and gc == c and qk == 'quota' and gk == 'challenge'
        end
        local function integer(v,minimum,maximum)
          local n=tonumber(v)
          if not n or n % 1 ~= 0 or n < minimum or n > maximum then return nil end
          return n
        end
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
        local function channelState(k,c,expiry,now)
          local state=redis.call('HGET',k,c..':state')
          local target=redis.call('HGET',k,c..':target')
          local generation=redis.call('HGET',k,c..':generation')
          local checks=redis.call('HGET',k,c..':checks')
          local code=redis.call('HGET',k,c..':code')
          local codeExpiry=redis.call('HGET',k,c..':codeExpiry')
          local proof=redis.call('HGET',k,c..':proof')
          local proofExpiry=redis.call('HGET',k,c..':proofExpiry')
          if not state then
            if target or generation or checks or code or codeExpiry or proof or proofExpiry then return false end
            return true,nil,0
          end
          local count=integer(checks,0,5)
          local latestExpiry=math.min(now+verificationTtlMs,expiry)
          if not digest(target) or not token(generation) or not count or not integer(codeExpiry,1,latestExpiry) then return false end
          if state == 'pending' or state == 'sent' then
            if not digest(code) or proof or proofExpiry then return false end
          elseif state == 'verified' then
            if code or count < 1 or not digest(proof) or not integer(proofExpiry,1,latestExpiry) then return false end
          elseif state == 'failed' then
            if code or proof or proofExpiry then return false end
          else return false end
          return true,state,count
        end
        local t=redis.call('TIME'); local now=tonumber(t[1])*1000+math.floor(tonumber(t[2])/1000)
        """;
    private static final String AUTH = """
        if not kind(KEYS[1],'hash') then return {'UNAVAILABLE'} end
        if not equal(redis.call('HGET',KEYS[1],'secret'),ARGV[1]) then return {'REQUIRED'} end
        local expiry=integer(redis.call('HGET',KEYS[1],'expiry'),1,9007199254740991)
        if not expiry then return {'UNAVAILABLE'} end
        if now >= expiry then return {'REQUIRED'} end
        if expiry > now+1800000 then return {'UNAVAILABLE'} end
        local attemptState=redis.call('HGET',KEYS[1],'state')
        if attemptState == 'consumed' then return {'RESTART'} end
        if attemptState ~= 'open' then return {'UNAVAILABLE'} end
        """;
    private static final Map<String, String> SCRIPTS = Map.of(
        "create", COMMON + """
            if #KEYS ~= 1 or #ARGV ~= 1 or not requestKeys() or not digest(ARGV[1]) then return {'UNAVAILABLE'} end
            if redis.call('EXISTS',KEYS[1]) == 1 then return {'COLLISION'} end
            local expiry=now+1800000
            redis.call('HSETNX',KEYS[1],'secret',ARGV[1])
            redis.call('HSET',KEYS[1],'expiry',expiry,'state','open')
            redis.call('PEXPIREAT',KEYS[1],expiry)
            return {'OK',tostring(expiry)}
            """,
        "issue", COMMON + """
            if #KEYS ~= 3 or #ARGV ~= 6 or KEYS[1] == KEYS[2] or KEYS[1] == KEYS[3] or KEYS[2] == KEYS[3]
              or not requestKeys(ARGV[2]) or not digest(ARGV[1]) or not channel(ARGV[2]) or not digest(ARGV[3])
              or not token(ARGV[4]) or not digest(ARGV[5]) or not token(ARGV[6]) then return {'UNAVAILABLE'} end
            """ + AUTH + """
            if not kind(KEYS[2],'zset') then return {'UNAVAILABLE'} end
            if redis.call('ZCARD',KEYS[2]) > 3 then return {'UNAVAILABLE'} end
            local quota=redis.call('ZRANGE',KEYS[2],0,-1,'WITHSCORES')
            for i=1,#quota,2 do
              if not token(quota[i]) or not integer(quota[i+1],1,now) then return {'UNAVAILABLE'} end
            end
            local valid,state,checks=channelState(KEYS[1],ARGV[2],expiry,now)
            if not valid then return {'UNAVAILABLE'} end
            local c=ARGV[2]; local target=redis.call('HGET',KEYS[1],c..':target')
            if target and not equal(target,ARGV[3]) then return {'REQUIRED'} end
            if checks >= 5 then return {'LIMIT','0'} end
            if redis.call('EXISTS',KEYS[3]) == 1 or redis.call('ZSCORE',KEYS[2],ARGV[6]) then return {'COLLISION'} end
            local recent=redis.call('ZRANGEBYSCORE',KEYS[2],'('..tostring(now-600000),'+inf','WITHSCORES')
            if #recent >= 6 then return {'LIMIT',tostring(math.max(1,math.ceil((tonumber(recent[2])+600000-now)/1000)))} end
            local codeExpiry=math.min(now+verificationTtlMs,expiry)
            redis.call('SET',KEYS[3],'reserved','NX','PX',expiry-now)
            redis.call('ZREMRANGEBYSCORE',KEYS[2],'-inf',now-600000)
            redis.call('ZADD',KEYS[2],'NX',now,ARGV[6]); redis.call('PEXPIRE',KEYS[2],600000)
            redis.call('HSET',KEYS[1],c..':target',ARGV[3],c..':generation',ARGV[4],c..':code',ARGV[5],c..':codeExpiry',codeExpiry,c..':checks',checks,c..':state','pending')
            redis.call('HDEL',KEYS[1],c..':proof',c..':proofExpiry')
            return {'OK',tostring(codeExpiry)}
            """,
        "complete", COMMON + """
            if #KEYS ~= 1 or #ARGV ~= 4 or not requestKeys() or not digest(ARGV[1]) or not channel(ARGV[2])
              or not token(ARGV[3]) or (ARGV[4] ~= 'sent' and ARGV[4] ~= 'failed') then return {'UNAVAILABLE'} end
            """ + AUTH + """
            local c=ARGV[2]
            local valid,state=channelState(KEYS[1],c,expiry,now)
            if not valid then return {'UNAVAILABLE'} end
            if redis.call('HGET',KEYS[1],c..':generation') ~= ARGV[3] or redis.call('HGET',KEYS[1],c..':state') ~= 'pending' then return {'RESTART'} end
            if ARGV[4] == 'sent' and now >= tonumber(redis.call('HGET',KEYS[1],c..':codeExpiry') or '0') then return {'REQUIRED'} end
            local state='failed'
            if ARGV[4] == 'sent' and now < tonumber(redis.call('HGET',KEYS[1],c..':codeExpiry') or '0') then state='sent' end
            redis.call('HSET',KEYS[1],c..':state',state)
            if state ~= 'sent' then redis.call('HDEL',KEYS[1],c..':code') end
            return {'OK'}
            """,
        "verify", COMMON + """
            if #KEYS ~= 1 or #ARGV ~= 5 or not requestKeys() or not digest(ARGV[1]) or not channel(ARGV[2])
              or not token(ARGV[3]) or not digest(ARGV[4]) or not digest(ARGV[5]) then return {'UNAVAILABLE'} end
            """ + AUTH + """
            local c=ARGV[2]; local valid,state,checks=channelState(KEYS[1],c,expiry,now)
            if not valid then return {'UNAVAILABLE'} end
            if state == 'verified' then return {'RESTART'} end
            if redis.call('HGET',KEYS[1],c..':generation') ~= ARGV[3] or state ~= 'sent' then return {'REQUIRED'} end
            if now >= tonumber(redis.call('HGET',KEYS[1],c..':codeExpiry') or '0') then return {'REQUIRED'} end
            if checks >= 5 then return {'LIMIT','0'} end
            local matches=equal(redis.call('HGET',KEYS[1],c..':code'),ARGV[4])
            redis.call('HSET',KEYS[1],c..':checks',checks+1)
            if not matches then
              if checks+1 >= 5 then return {'LIMIT','0'} end
              return {'REQUIRED'}
            end
            local proofExpiry=math.min(now+verificationTtlMs,expiry)
            redis.call('HSET',KEYS[1],c..':proof',ARGV[5],c..':proofExpiry',proofExpiry,c..':state','verified')
            redis.call('HDEL',KEYS[1],c..':code')
            return {'OK',tostring(proofExpiry)}
            """,
        "consume", COMMON + """
            if #KEYS ~= 1 or #ARGV ~= 5 or not requestKeys() then return {'UNAVAILABLE'} end
            for i=1,5 do if not digest(ARGV[i]) then return {'UNAVAILABLE'} end end
            """ + AUTH + """
            for i=1,2 do
              local c=({'email','phone'})[i]; local offset=2+(i-1)*2
              local valid,state=channelState(KEYS[1],c,expiry,now)
              if not valid then return {'UNAVAILABLE'} end
              if state ~= 'verified'
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
