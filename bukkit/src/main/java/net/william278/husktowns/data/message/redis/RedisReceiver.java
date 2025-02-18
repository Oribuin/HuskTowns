package net.william278.husktowns.data.message.redis;

import net.william278.husktowns.HuskTowns;
import net.william278.husktowns.data.message.CrossServerMessageHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.Level;

public class RedisReceiver {

    private static JedisPool jedisPool;

    public static Jedis getJedis() {
        return jedisPool.getResource();
    }

    // Initialize a JedisPool that can be drawn from on demand
    public static void initialize() {
        if (HuskTowns.getSettings().redisPassword.isEmpty()) {
            jedisPool = new JedisPool(new JedisPoolConfig(),
                    HuskTowns.getSettings().redisHost,
                    HuskTowns.getSettings().redisPort,
                    0,
                    HuskTowns.getSettings().redisSsl);
        } else {
            jedisPool = new JedisPool(new JedisPoolConfig(),
                    HuskTowns.getSettings().redisHost,
                    HuskTowns.getSettings().redisPort,
                    0,
                    HuskTowns.getSettings().redisPassword,
                    HuskTowns.getSettings().redisSsl);
        }
    }

    // Close the connection
    public static void terminate() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }

    private static final HuskTowns plugin = HuskTowns.getInstance();
    public static final String REDIS_CHANNEL = "HuskTowns";

    // Get a player to act as the "target" (ensure compatability with Plugin Messaging)
    private static Player getRandomReceiver() {
        ArrayList<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            return null;
        }
        Collections.shuffle(players);
        return players.get(0);
    }

    public static void listen() {
        new Thread(() -> {
            try (Jedis jedis = getJedis()) {
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        if (!channel.equals(REDIS_CHANNEL)) {
                            return;
                        }

                        /* HuskTowns Redis messages have a header formatted as such:
                         * <cluster_id>:<message_type>:<target_player>£ followed by the message arguments and data. */
                        final String[] splitMessage = message.split(RedisMessage.REDIS_MESSAGE_HEADER_SEPARATOR);
                        final String messageHeader = splitMessage[0];
                        int clusterID;

                        // Ensure the cluster ID matches
                        try {
                            clusterID = Integer.parseInt(messageHeader.split(":")[0]);
                        } catch (Exception e) {
                            // In case the message is malformed or the cluster ID is invalid
                            HuskTowns.getInstance().getLogger().warning("Received a Redis message on the HuskTowns channel with an invalid server Cluster ID! \n" +
                                    "Please ensure that the cluster ID is set to a valid integer on all servers.");
                            return;
                        }
                        if (HuskTowns.getSettings().clusterId != clusterID) {
                            return;
                        }

                        // Get the type of redis message
                        final String messageType = messageHeader.split(":")[1];

                        // Get the player targeted by the message and make sure they are online
                        final String target = messageHeader.split(":")[2];
                        Player receiver = Bukkit.getPlayerExact(target);

                        // If the redis message was targeting this server
                        if (target.equalsIgnoreCase("-all-")) {
                            receiver = getRandomReceiver();
                        } else if (target.contains("server-")) {
                            if (target.split("-")[1].equalsIgnoreCase(HuskTowns.getSettings().serverId)) {
                                receiver = getRandomReceiver();
                            } else {
                                return; // The message was targeting another server; ignore
                            }
                        }

                        // Return if the target is not available
                        if (receiver == null) {
                            return;
                        }
                        if (!receiver.isOnline()) {
                            return;
                        }

                        final String messageData = splitMessage[1];
                        CrossServerMessageHandler.handleMessage(new RedisMessage(clusterID, receiver.getName(),
                                messageType, messageData), receiver);
                    }
                }, REDIS_CHANNEL);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "An exception occurred in the Jedis subscriber", e);
            }
        }, "Redis Subscriber").start();
    }
}