package co.threathub.ingestor.util;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Slf4j
public class JedisManager {
    private static final JedisPool pool;

    static {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(50);
        config.setMaxIdle(10);
        config.setMinIdle(2);

        pool = new JedisPool(config, "localhost", 6379);
    }

    public static Jedis getConnection() {
        return pool.getResource();
    }

    public static void shutdown() {
        pool.close();
    }
}