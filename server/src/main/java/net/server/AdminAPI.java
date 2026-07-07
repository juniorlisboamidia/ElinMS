package net.server;

import client.Character;
import client.Skill;
import client.SkillFactory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import constants.inventory.ItemConstants;
import net.server.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ItemInformationProvider;
import server.TimerManager;
import server.life.LifeFactory;
import server.life.NPC;
import server.maps.MapleMap;

import tools.DatabaseConnection;
import tools.PacketCreator;

import java.awt.Point;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Lightweight HTTP API for the dashboard to control the game server.
 * Listens on port 8585 (internal network only).
 */
public class AdminAPI {
    private static final Logger log = LoggerFactory.getLogger(AdminAPI.class);
    private static final int PORT = 8585;
    private HttpServer server;

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/rates", this::handleRates);
            server.createContext("/status", this::handleStatus);
            server.createContext("/drop", this::handleDrop);
            server.createContext("/message", this::handleMessage);
            server.createContext("/npc", this::handleNpcSpawn);
            server.createContext("/warp", this::handleWarp);
            server.createContext("/giveitem", this::handleGiveItem);
            server.createContext("/spawnmob", this::handleSpawnMob);
            server.createContext("/heal", this::handleHeal);
            server.createContext("/givemeso", this::handleGiveMeso);
            server.createContext("/buff", this::handleBuff);
            server.createContext("/kick", this::handleKick);
            server.createContext("/msgplayer", this::handleMsgPlayer);
            server.setExecutor(null);
            server.start();
            log.info("Admin API started on port {}", PORT);
            loadRatesFromDb();
            loadServerMessageFromDb();
        } catch (IOException e) {
            log.error("Failed to start Admin API", e);
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    private void handleRates(HttpExchange ex) throws IOException {
        if ("GET".equals(ex.getRequestMethod())) {
            getRates(ex);
        } else if ("PUT".equals(ex.getRequestMethod())) {
            setRates(ex);
        } else {
            respond(ex, 405, "{\"error\":\"Method not allowed\"}");
        }
    }

    private void getRates(HttpExchange ex) throws IOException {
        World world = Server.getInstance().getWorld(0);
        if (world == null) {
            respond(ex, 500, "{\"error\":\"World 0 not found\"}");
            return;
        }
        String json = String.format(
            "{\"exp_rate\":%d,\"meso_rate\":%d,\"drop_rate\":%d,\"boss_drop_rate\":%d}",
            world.getExpRate(), world.getMesoRate(), world.getDropRate(), world.getBossDropRate()
        );
        respond(ex, 200, json);
    }

    private void setRates(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        World world = Server.getInstance().getWorld(0);
        if (world == null) {
            respond(ex, 500, "{\"error\":\"World 0 not found\"}");
            return;
        }

        // Simple JSON parsing (no dependencies needed)
        Integer expRate = extractInt(body, "exp_rate");
        Integer mesoRate = extractInt(body, "meso_rate");
        Integer dropRate = extractInt(body, "drop_rate");
        Integer bossDropRate = extractInt(body, "boss_drop_rate");

        StringBuilder changes = new StringBuilder();
        if (expRate != null) { world.setExpRate(expRate); changes.append("exp_rate=").append(expRate).append(" "); }
        if (mesoRate != null) { world.setMesoRate(mesoRate); changes.append("meso_rate=").append(mesoRate).append(" "); }
        if (dropRate != null) { world.setDropRate(dropRate); changes.append("drop_rate=").append(dropRate).append(" "); }
        if (bossDropRate != null) { world.setBossDropRate(bossDropRate); changes.append("boss_drop_rate=").append(bossDropRate).append(" "); }

        if (changes.length() == 0) {
            respond(ex, 400, "{\"error\":\"No valid rates provided\"}");
            return;
        }

        log.info("Admin API: Rates updated — {}", changes.toString().trim());
        saveRatesToDb(world);
        respond(ex, 200, String.format(
            "{\"success\":true,\"exp_rate\":%d,\"meso_rate\":%d,\"drop_rate\":%d,\"boss_drop_rate\":%d}",
            world.getExpRate(), world.getMesoRate(), world.getDropRate(), world.getBossDropRate()
        ));
    }

    private void handleStatus(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        Server srv = Server.getInstance();
        int totalPlayers = 0;
        for (World w : srv.getWorlds()) {
            totalPlayers += w.getPlayerStorage().getAllCharacters().size();
        }
        World w0 = srv.getWorld(0);
        String json = String.format(
            "{\"online\":true,\"players\":%d,\"exp_rate\":%d,\"meso_rate\":%d,\"drop_rate\":%d}",
            totalPlayers,
            w0 != null ? w0.getExpRate() : 0,
            w0 != null ? w0.getMesoRate() : 0,
            w0 != null ? w0.getDropRate() : 0
        );
        respond(ex, 200, json);
    }

    private void handleDrop(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        // Parse required fields
        Integer itemId = extractInt(body, "itemId");
        Integer quantity = extractInt(body, "quantity");
        String characterName = extractString(body, "characterName");
        Integer characterId = extractInt(body, "characterId");
        Integer mapId = extractInt(body, "mapId");
        Integer x = extractInt(body, "x");
        Integer y = extractInt(body, "y");
        Integer worldId = extractInt(body, "world");

        if (itemId == null) {
            respond(ex, 400, "{\"error\":\"itemId is required\"}");
            return;
        }
        if (quantity == null || quantity < 1) quantity = 1;
        if (worldId == null) worldId = 0;

        World world = Server.getInstance().getWorld(worldId);
        if (world == null) {
            respond(ex, 500, "{\"error\":\"World not found\"}");
            return;
        }

        // Find the target character (by name or id)
        Character target = null;
        if (characterName != null) {
            target = world.getPlayerStorage().getCharacterByName(characterName);
        } else if (characterId != null) {
            target = world.getPlayerStorage().getCharacterById(characterId);
        }

        // Target must be an online player (needed as dropper/owner for the drop packet)
        if (target == null) {
            // If characterName/Id didn't find anyone, and we have mapId+x+y, try to find ANY player on that map
            if (mapId != null && x != null && y != null) {
                for (var ch : world.getChannels()) {
                    MapleMap m = ch.getMapFactory().getMap(mapId);
                    var players = m.getAllPlayers();
                    if (!players.isEmpty()) {
                        target = players.get(0);
                        break;
                    }
                }
            }
            if (target == null) {
                respond(ex, 400, "{\"error\":\"No online player found. Provide characterName/characterId of an online player, or ensure someone is on the target map.\"}");
                return;
            }
        }

        // Determine map and drop position
        MapleMap map = target.getMap();
        Point dropPos;

        if (mapId != null && x != null && y != null) {
            // Use explicit map + coords if given
            map = target.getClient().getChannelServer().getMapFactory().getMap(mapId);
            dropPos = new Point(x, y);
        } else {
            // Drop in front of the character
            Point pos = target.getPosition();
            dropPos = new Point(pos.x + 30, pos.y);
        }

        // Create item — use proper equip generation for equip items (matching !drop command)
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        int qty = Math.min(quantity, 999);
        Item toDrop;
        if (ItemConstants.getInventoryType(itemId) == InventoryType.EQUIP) {
            toDrop = ii.getEquipById(itemId);
        } else {
            toDrop = new Item(itemId, (short) 0, (short) qty);
        }
        toDrop.setOwner("");

        // Spawn on the map's event thread to match how GM commands work
        final Character dropTarget = target;
        final MapleMap dropMap = map;
        final Point finalDropPos = dropPos;
        // Use TimerManager to run on game thread
        TimerManager.getInstance().schedule(() -> {
            dropMap.spawnItemDrop(dropTarget, dropTarget, toDrop, finalDropPos, true, true);
        }, 0);

        log.info("Admin API: Dropped {}x item {} on map {} at ({},{})", quantity, itemId, map.getId(), dropPos.x, dropPos.y);
        respond(ex, 200, String.format(
            "{\"success\":true,\"itemId\":%d,\"quantity\":%d,\"mapId\":%d,\"x\":%d,\"y\":%d}",
            itemId, quantity, map.getId(), dropPos.x, dropPos.y
        ));
    }

    private void handleMessage(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String message = extractString(body, "message");

        if (message == null || message.isEmpty()) {
            respond(ex, 400, "{\"error\":\"message is required\"}");
            return;
        }

        World world = Server.getInstance().getWorld(0);
        if (world == null) {
            respond(ex, 500, "{\"error\":\"World 0 not found\"}");
            return;
        }

        world.setServerMessage(message);
        saveServerMessageToDb(message);
        log.info("Admin API: Server message updated — {}", message);
        respond(ex, 200, String.format("{\"success\":true,\"message\":\"%s\"}", message.replace("\"", "\\\"")));
    }

    private void spawnNpcOnMap(int mapId, int npcId, int x, int y, int fh) {
        World world = Server.getInstance().getWorld(0);
        if (world == null) return;
        for (var ch : world.getChannels()) {
            try {
                MapleMap map = ch.getMapFactory().getMap(mapId);
                NPC npc = LifeFactory.getNPC(npcId);
                if (npc == null) continue;
                npc.setPosition(new Point(x, y));
                npc.setCy(y);
                npc.setRx0(x - 50);
                npc.setRx1(x + 50);
                npc.setFh(fh);
                map.addMapObject(npc);
                map.broadcastMessage(PacketCreator.spawnNPC(npc));
                log.info("Spawned custom NPC {} on map {} ch{} at ({},{})", npcId, mapId, ch.getId(), x, y);
            } catch (Exception e) {
                log.warn("Failed to spawn NPC {} on map {} ch{}", npcId, mapId, ch.getId(), e);
            }
        }
    }

    private void handleNpcSpawn(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Integer npcId = extractInt(body, "npcId");
        Integer mapId = extractInt(body, "mapId");
        Integer x = extractInt(body, "x");
        Integer y = extractInt(body, "y");
        Integer fh = extractInt(body, "fh");

        if (npcId == null || mapId == null || x == null || y == null) {
            respond(ex, 400, "{\"error\":\"npcId, mapId, x, y are required\"}");
            return;
        }
        spawnNpcOnMap(mapId, npcId, x, y, fh != null ? fh : 0);
        respond(ex, 200, String.format("{\"success\":true,\"npcId\":%d,\"mapId\":%d}", npcId, mapId));
    }

    private void handleWarp(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String characterName = extractString(body, "characterName");
        Integer characterId = extractInt(body, "characterId");
        Integer mapId = extractInt(body, "mapId");
        Integer worldId = extractInt(body, "world");

        if (mapId == null) {
            respond(ex, 400, "{\"error\":\"mapId is required\"}");
            return;
        }
        if (worldId == null) worldId = 0;

        World world = Server.getInstance().getWorld(worldId);
        if (world == null) {
            respond(ex, 500, "{\"error\":\"World not found\"}");
            return;
        }

        Character target = null;
        if (characterName != null) {
            target = world.getPlayerStorage().getCharacterByName(characterName);
        } else if (characterId != null) {
            target = world.getPlayerStorage().getCharacterById(characterId);
        }
        if (target == null) {
            respond(ex, 400, "{\"error\":\"No online player found. Provide characterName/characterId of an ONLINE player.\"}");
            return;
        }
        if (!target.isAlive()) {
            respond(ex, 400, "{\"error\":\"Player is dead; cannot warp right now.\"}");
            return;
        }

        MapleMap dest = target.getClient().getChannelServer().getMapFactory().getMap(mapId);
        if (dest == null) {
            respond(ex, 400, String.format("{\"error\":\"Map ID %d is invalid\"}", mapId));
            return;
        }

        final Character warpTarget = target;
        final MapleMap warpDest = dest;
        // Run on the game thread (matches how !warp / handleDrop mutate live state)
        TimerManager.getInstance().schedule(() -> {
            try {
                warpTarget.saveLocationOnWarp();
                warpTarget.changeMap(warpDest, warpDest.getRandomPlayerSpawnpoint());
            } catch (Exception e) {
                log.warn("Admin API: warp failed for {}", warpTarget.getName(), e);
            }
        }, 0);

        log.info("Admin API: Warped {} to map {}", target.getName(), mapId);
        respond(ex, 200, String.format(
            "{\"success\":true,\"character\":\"%s\",\"mapId\":%d}",
            target.getName().replace("\"", "\\\""), mapId
        ));
    }

    private void handleGiveItem(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Integer itemId = extractInt(body, "itemId");
        Integer quantity = extractInt(body, "quantity");
        String characterName = extractString(body, "characterName");
        Integer characterId = extractInt(body, "characterId");
        Integer worldId = extractInt(body, "world");

        if (itemId == null) {
            respond(ex, 400, "{\"error\":\"itemId is required\"}");
            return;
        }
        if (quantity == null || quantity < 1) quantity = 1;
        if (worldId == null) worldId = 0;

        World world = Server.getInstance().getWorld(worldId);
        if (world == null) {
            respond(ex, 500, "{\"error\":\"World not found\"}");
            return;
        }

        Character target = null;
        if (characterName != null) {
            target = world.getPlayerStorage().getCharacterByName(characterName);
        } else if (characterId != null) {
            target = world.getPlayerStorage().getCharacterById(characterId);
        }
        if (target == null) {
            respond(ex, 400, "{\"error\":\"No online player found. Provide characterName/characterId of an ONLINE player.\"}");
            return;
        }

        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        if (ii.getName(itemId) == null) {
            respond(ex, 400, String.format("{\"error\":\"Item ID %d does not exist\"}", itemId));
            return;
        }

        final Character giveTarget = target;
        final int giveItemId = itemId;
        final short giveQty = (short) Math.min(quantity, 999);
        // addById handles inventory type + equip generation and pushes the update live
        TimerManager.getInstance().schedule(() -> {
            try {
                InventoryManipulator.addById(giveTarget.getClient(), giveItemId, giveQty, "", -1, (short) 0, -1L);
            } catch (Exception e) {
                log.warn("Admin API: giveitem failed for {}", giveTarget.getName(), e);
            }
        }, 0);

        log.info("Admin API: Gave {}x item {} to {}", quantity, itemId, target.getName());
        respond(ex, 200, String.format(
            "{\"success\":true,\"character\":\"%s\",\"itemId\":%d,\"quantity\":%d}",
            target.getName().replace("\"", "\\\""), itemId, quantity
        ));
    }

    private Character findOnlinePlayer(World world, String characterName, Integer characterId) {
        if (characterName != null) {
            return world.getPlayerStorage().getCharacterByName(characterName);
        } else if (characterId != null) {
            return world.getPlayerStorage().getCharacterById(characterId);
        }
        return null;
    }

    private void handleSpawnMob(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Integer mobId = extractInt(body, "mobId");
        Integer quantity = extractInt(body, "quantity");
        String characterName = extractString(body, "characterName");
        Integer characterId = extractInt(body, "characterId");
        Integer worldId = extractInt(body, "world");

        if (mobId == null) {
            respond(ex, 400, "{\"error\":\"mobId is required\"}");
            return;
        }
        if (quantity == null || quantity < 1) quantity = 1;
        quantity = Math.min(quantity, 30);
        if (worldId == null) worldId = 0;

        World world = Server.getInstance().getWorld(worldId);
        if (world == null) {
            respond(ex, 500, "{\"error\":\"World not found\"}");
            return;
        }

        Character target = findOnlinePlayer(world, characterName, characterId);
        if (target == null) {
            respond(ex, 400, "{\"error\":\"No online player found. Provide characterName/characterId of an ONLINE player.\"}");
            return;
        }
        if (LifeFactory.getMonster(mobId) == null) {
            respond(ex, 400, String.format("{\"error\":\"Mob ID %d does not exist\"}", mobId));
            return;
        }

        final Character spawnTarget = target;
        final int fMobId = mobId;
        final int fQty = quantity;
        // spawnMonsterOnGroundBelow mutates the map — run on the game thread. Live, no restart.
        TimerManager.getInstance().schedule(() -> {
            try {
                MapleMap map = spawnTarget.getMap();
                Point pos = spawnTarget.getPosition();
                for (int i = 0; i < fQty; i++) {
                    map.spawnMonsterOnGroundBelow(LifeFactory.getMonster(fMobId), pos);
                }
            } catch (Exception e) {
                log.warn("Admin API: spawn mob failed for {}", spawnTarget.getName(), e);
            }
        }, 0);

        log.info("Admin API: Spawned {}x mob {} at {}", quantity, mobId, target.getName());
        respond(ex, 200, String.format(
            "{\"success\":true,\"mobId\":%d,\"quantity\":%d,\"mapId\":%d}",
            mobId, quantity, target.getMap().getId()
        ));
    }

    private void handleHeal(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String characterName = extractString(body, "characterName");
        Integer characterId = extractInt(body, "characterId");
        Integer worldId = extractInt(body, "world");
        if (worldId == null) worldId = 0;

        World world = Server.getInstance().getWorld(worldId);
        if (world == null) {
            respond(ex, 500, "{\"error\":\"World not found\"}");
            return;
        }

        Character target = findOnlinePlayer(world, characterName, characterId);
        if (target == null) {
            respond(ex, 400, "{\"error\":\"No online player found. Provide characterName/characterId of an ONLINE player.\"}");
            return;
        }

        final Character healTarget = target;
        TimerManager.getInstance().schedule(() -> {
            try {
                healTarget.healHpMp();
            } catch (Exception e) {
                log.warn("Admin API: heal failed for {}", healTarget.getName(), e);
            }
        }, 0);

        log.info("Admin API: Healed {}", target.getName());
        respond(ex, 200, String.format("{\"success\":true,\"character\":\"%s\"}", target.getName().replace("\"", "\\\"")));
    }

    private void handleGiveMeso(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Integer amount = extractInt(body, "amount");
        String characterName = extractString(body, "characterName");
        Integer characterId = extractInt(body, "characterId");
        Integer worldId = extractInt(body, "world");

        if (amount == null) {
            respond(ex, 400, "{\"error\":\"amount is required\"}");
            return;
        }
        if (worldId == null) worldId = 0;

        World world = Server.getInstance().getWorld(worldId);
        if (world == null) {
            respond(ex, 500, "{\"error\":\"World not found\"}");
            return;
        }

        Character target = findOnlinePlayer(world, characterName, characterId);
        if (target == null) {
            respond(ex, 400, "{\"error\":\"No online player found. Provide characterName/characterId of an ONLINE player.\"}");
            return;
        }

        final Character mesoTarget = target;
        final int fAmount = amount;
        TimerManager.getInstance().schedule(() -> {
            try {
                mesoTarget.gainMeso(fAmount, true);
            } catch (Exception e) {
                log.warn("Admin API: give meso failed for {}", mesoTarget.getName(), e);
            }
        }, 0);

        log.info("Admin API: Gave {} meso to {}", amount, target.getName());
        respond(ex, 200, String.format(
            "{\"success\":true,\"character\":\"%s\",\"amount\":%d}",
            target.getName().replace("\"", "\\\""), amount
        ));
    }

    private void handleBuff(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Integer skillId = extractInt(body, "skillId");
        String characterName = extractString(body, "characterName");
        Integer characterId = extractInt(body, "characterId");
        Integer worldId = extractInt(body, "world");

        if (skillId == null) {
            respond(ex, 400, "{\"error\":\"skillId is required\"}");
            return;
        }
        if (worldId == null) worldId = 0;

        World world = Server.getInstance().getWorld(worldId);
        if (world == null) {
            respond(ex, 500, "{\"error\":\"World not found\"}");
            return;
        }

        Character target = findOnlinePlayer(world, characterName, characterId);
        if (target == null) {
            respond(ex, 400, "{\"error\":\"No online player found. Provide characterName/characterId of an ONLINE player.\"}");
            return;
        }
        Skill skill = SkillFactory.getSkill(skillId);
        if (skill == null) {
            respond(ex, 400, String.format("{\"error\":\"Skill ID %d does not exist\"}", skillId));
            return;
        }

        final Character buffTarget = target;
        final Skill buffSkill = skill;
        TimerManager.getInstance().schedule(() -> {
            try {
                buffSkill.getEffect(buffSkill.getMaxLevel()).applyTo(buffTarget);
            } catch (Exception e) {
                log.warn("Admin API: buff failed for {}", buffTarget.getName(), e);
            }
        }, 0);

        log.info("Admin API: Buffed {} with skill {}", target.getName(), skillId);
        respond(ex, 200, String.format(
            "{\"success\":true,\"character\":\"%s\",\"skillId\":%d}",
            target.getName().replace("\"", "\\\""), skillId
        ));
    }

    private void handleKick(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String characterName = extractString(body, "characterName");
        Integer characterId = extractInt(body, "characterId");
        Integer worldId = extractInt(body, "world");
        if (worldId == null) worldId = 0;

        World world = Server.getInstance().getWorld(worldId);
        if (world == null) {
            respond(ex, 500, "{\"error\":\"World not found\"}");
            return;
        }

        Character target = findOnlinePlayer(world, characterName, characterId);
        if (target == null) {
            respond(ex, 400, "{\"error\":\"No online player found. Provide characterName/characterId of an ONLINE player.\"}");
            return;
        }

        final Character kickTarget = target;
        final String kickName = target.getName();
        TimerManager.getInstance().schedule(() -> {
            try {
                kickTarget.getClient().disconnect(false, false);
            } catch (Exception e) {
                log.warn("Admin API: kick failed for {}", kickName, e);
            }
        }, 0);

        log.info("Admin API: Kicked {}", kickName);
        respond(ex, 200, String.format("{\"success\":true,\"character\":\"%s\"}", kickName.replace("\"", "\\\"")));
    }

    private void handleMsgPlayer(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String message = extractString(body, "message");
        Integer msgType = extractInt(body, "type");
        String characterName = extractString(body, "characterName");
        Integer characterId = extractInt(body, "characterId");
        Integer worldId = extractInt(body, "world");

        if (message == null || message.isEmpty()) {
            respond(ex, 400, "{\"error\":\"message is required\"}");
            return;
        }
        if (worldId == null) worldId = 0;

        World world = Server.getInstance().getWorld(worldId);
        if (world == null) {
            respond(ex, 500, "{\"error\":\"World not found\"}");
            return;
        }

        Character target = findOnlinePlayer(world, characterName, characterId);
        if (target == null) {
            respond(ex, 400, "{\"error\":\"No online player found. Provide characterName/characterId of an ONLINE player.\"}");
            return;
        }

        final Character msgTarget = target;
        final String msgText = message;
        final int msgKind = (msgType != null) ? msgType : 6; // 6 = light-blue GM text
        TimerManager.getInstance().schedule(() -> {
            try {
                msgTarget.dropMessage(msgKind, msgText);
            } catch (Exception e) {
                log.warn("Admin API: message failed for {}", msgTarget.getName(), e);
            }
        }, 0);

        log.info("Admin API: Messaged {} — {}", target.getName(), message);
        respond(ex, 200, String.format("{\"success\":true,\"character\":\"%s\"}", target.getName().replace("\"", "\\\"")));
    }

    private void loadRatesFromDb() {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT config_key, config_value FROM server_config WHERE config_key IN ('exp_rate','meso_rate','drop_rate','boss_drop_rate')");
             ResultSet rs = ps.executeQuery()) {
            World world = Server.getInstance().getWorld(0);
            if (world == null) return;
            boolean any = false;
            while (rs.next()) {
                String key = rs.getString("config_key");
                try {
                    int val = Integer.parseInt(rs.getString("config_value"));
                    switch (key) {
                        case "exp_rate" -> world.setExpRate(val);
                        case "meso_rate" -> world.setMesoRate(val);
                        case "drop_rate" -> world.setDropRate(val);
                        case "boss_drop_rate" -> world.setBossDropRate(val);
                    }
                    any = true;
                } catch (NumberFormatException ignored) {}
            }
            if (any) {
                log.info("Admin API: Loaded rates from DB — exp={} meso={} drop={} boss_drop={}",
                    world.getExpRate(), world.getMesoRate(), world.getDropRate(), world.getBossDropRate());
            }
        } catch (SQLException e) {
            log.warn("Admin API: Could not load rates from DB (table may not exist yet)", e);
        }
    }

    private void saveRatesToDb(World world) {
        String upsert = "INSERT INTO server_config (config_key, config_value) VALUES (?, ?) ON DUPLICATE KEY UPDATE config_value = VALUES(config_value)";
        try (Connection con = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(upsert)) {
                String[][] pairs = {
                    {"exp_rate", String.valueOf(world.getExpRate())},
                    {"meso_rate", String.valueOf(world.getMesoRate())},
                    {"drop_rate", String.valueOf(world.getDropRate())},
                    {"boss_drop_rate", String.valueOf(world.getBossDropRate())}
                };
                for (String[] pair : pairs) {
                    ps.setString(1, pair[0]);
                    ps.setString(2, pair[1]);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (SQLException e) {
            log.warn("Admin API: Could not save rates to DB", e);
        }
    }

    private void loadServerMessageFromDb() {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT config_value FROM server_config WHERE config_key = 'server_message'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String msg = rs.getString("config_value");
                if (msg != null && !msg.isEmpty()) {
                    World world = Server.getInstance().getWorld(0);
                    if (world != null) {
                        world.setServerMessage(msg);
                        log.info("Admin API: Loaded server message from DB — {}", msg);
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("Admin API: Could not load server message from DB (table may not exist yet)", e);
        }
    }

    private void saveServerMessageToDb(String message) {
        String upsert = "INSERT INTO server_config (config_key, config_value) VALUES (?, ?) ON DUPLICATE KEY UPDATE config_value = VALUES(config_value)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(upsert)) {
            ps.setString(1, "server_message");
            ps.setString(2, message);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("Admin API: Could not save server message to DB", e);
        }
    }

    private static String extractString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    private static Integer extractInt(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '-' || (c >= '0' && c <= '9')) sb.append(c);
            else if (sb.length() > 0) break;
        }
        if (sb.length() == 0) return null;
        try { return Integer.parseInt(sb.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static void respond(HttpExchange ex, int code, String body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
