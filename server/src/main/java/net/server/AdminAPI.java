package net.server;

import client.Character;
import client.Job;
import client.Skill;
import client.SkillFactory;
import client.Stat;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import constants.id.MapId;
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
            server.createContext("/givefame", this::handleGiveFame);
            server.createContext("/jail", this::handleJail);
            server.createContext("/mutemap", this::handleMuteMap);
            server.createContext("/setjob", this::handleSetJob);
            server.createContext("/setlevel", this::handleSetLevel);
            server.createContext("/players", this::handlePlayers);
            server.createContext("/maxstats", this::handleMaxStats);
            server.createContext("/giveskill", this::handleGiveSkill);
            server.createContext("/cure", this::handleCure);
            server.createContext("/setgm", this::handleSetGm);
            server.createContext("/hide", this::handleHide);
            server.createContext("/unjail", this::handleUnjail);
            server.createContext("/killmobs", this::handleKillMobs);
            server.createContext("/cleardrops", this::handleClearDrops);
            server.createContext("/mapeffect", this::handleMapEffect);
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

    private void handleGiveFame(HttpExchange ex) throws IOException {
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

        final Character fameTarget = target;
        final int fameDelta = amount;
        TimerManager.getInstance().schedule(() -> {
            try {
                fameTarget.gainFame(fameDelta);
            } catch (Exception e) {
                log.warn("Admin API: give fame failed for {}", fameTarget.getName(), e);
            }
        }, 0);

        log.info("Admin API: Gave {} fame to {}", amount, target.getName());
        respond(ex, 200, String.format(
            "{\"success\":true,\"character\":\"%s\",\"amount\":%d}",
            target.getName().replace("\"", "\\\""), amount
        ));
    }

    private void handleJail(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Integer minutes = extractInt(body, "minutes");
        String characterName = extractString(body, "characterName");
        Integer characterId = extractInt(body, "characterId");
        Integer worldId = extractInt(body, "world");

        if (minutes == null || minutes < 1) minutes = 5;
        minutes = Math.min(minutes, 1440);
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

        final Character jailTarget = target;
        final int jailMinutes = minutes;
        TimerManager.getInstance().schedule(() -> {
            try {
                jailTarget.addJailExpirationTime(jailMinutes * 60000L);
                if (jailTarget.getMapId() != MapId.JAIL) {
                    MapleMap jailMap = jailTarget.getClient().getChannelServer().getMapFactory().getMap(MapId.JAIL);
                    jailTarget.saveLocationOnWarp();
                    jailTarget.changeMap(jailMap, jailMap.getPortal(0));
                }
            } catch (Exception e) {
                log.warn("Admin API: jail failed for {}", jailTarget.getName(), e);
            }
        }, 0);

        log.info("Admin API: Jailed {} for {} min", target.getName(), minutes);
        respond(ex, 200, String.format(
            "{\"success\":true,\"character\":\"%s\",\"minutes\":%d}",
            target.getName().replace("\"", "\\\""), minutes
        ));
    }

    private void handleMuteMap(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Integer mutedInt = extractInt(body, "muted");
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

        final boolean isMuted = (mutedInt == null || mutedInt != 0); // default true
        final Character muteTarget = target;
        TimerManager.getInstance().schedule(() -> {
            try {
                muteTarget.getMap().setMuted(isMuted);
            } catch (Exception e) {
                log.warn("Admin API: mute map failed for {}", muteTarget.getName(), e);
            }
        }, 0);

        log.info("Admin API: {} the map of {}", isMuted ? "Muted" : "Unmuted", target.getName());
        respond(ex, 200, String.format(
            "{\"success\":true,\"character\":\"%s\",\"muted\":%b}",
            target.getName().replace("\"", "\\\""), isMuted
        ));
    }

    private void handleSetJob(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Integer jobId = extractInt(body, "jobId");
        String characterName = extractString(body, "characterName");
        Integer characterId = extractInt(body, "characterId");
        Integer worldId = extractInt(body, "world");

        if (jobId == null) {
            respond(ex, 400, "{\"error\":\"jobId is required\"}");
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

        Job newJob = (jobId >= 0 && jobId < 2200) ? Job.getById(jobId) : null;
        if (newJob == null) {
            respond(ex, 400, String.format("{\"error\":\"Job ID %d is invalid (must be a real job, 0-2199)\"}", jobId));
            return;
        }

        final Character jobTarget = target;
        final Job job = newJob;
        // changeJob does the full class change live (skills, SP, packets); equipChanged refreshes look
        TimerManager.getInstance().schedule(() -> {
            try {
                jobTarget.changeJob(job);
                jobTarget.equipChanged();
            } catch (Exception e) {
                log.warn("Admin API: set job failed for {}", jobTarget.getName(), e);
            }
        }, 0);

        log.info("Admin API: Changed job of {} to {}", target.getName(), jobId);
        respond(ex, 200, String.format(
            "{\"success\":true,\"character\":\"%s\",\"jobId\":%d}",
            target.getName().replace("\"", "\\\""), jobId
        ));
    }

    private void handleSetLevel(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Integer level = extractInt(body, "level");
        String characterName = extractString(body, "characterName");
        Integer characterId = extractInt(body, "characterId");
        Integer worldId = extractInt(body, "world");

        if (level == null) {
            respond(ex, 400, "{\"error\":\"level is required\"}");
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

        final int targetLevel = Math.min(level, 255);
        final int currentLevel = target.getLevel();
        if (targetLevel <= currentLevel) {
            respond(ex, 400, String.format(
                "{\"error\":\"set_level only raises level live (player is level %d). To set a lower level, use update_character while the player is offline.\"}",
                currentLevel
            ));
            return;
        }

        final Character lvlTarget = target;
        // Loop the natural level-up so AP (status points), SP (skill points), HP/MP and packets all apply
        TimerManager.getInstance().schedule(() -> {
            try {
                for (int i = currentLevel; i < targetLevel; i++) {
                    lvlTarget.levelUp(false);
                }
            } catch (Exception e) {
                log.warn("Admin API: set level failed for {}", lvlTarget.getName(), e);
            }
        }, 0);

        log.info("Admin API: Leveled {} from {} to {}", target.getName(), currentLevel, targetLevel);
        respond(ex, 200, String.format(
            "{\"success\":true,\"character\":\"%s\",\"fromLevel\":%d,\"toLevel\":%d}",
            target.getName().replace("\"", "\\\""), currentLevel, targetLevel
        ));
    }

    private void handlePlayers(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"players\":[");
        boolean first = true;
        for (World w : Server.getInstance().getWorlds()) {
            for (Character chr : w.getPlayerStorage().getAllCharacters()) {
                if (!first) sb.append(",");
                first = false;
                sb.append(String.format(
                    "{\"id\":%d,\"name\":\"%s\",\"level\":%d,\"job\":%d,\"mapId\":%d}",
                    chr.getId(), chr.getName().replace("\"", "\\\""), chr.getLevel(), chr.getJob().getId(), chr.getMapId()
                ));
            }
        }
        sb.append("]}");
        respond(ex, 200, sb.toString());
    }

    private void handleMaxStats(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { respond(ex, 405, "{\"error\":\"Method not allowed\"}"); return; }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Integer worldId = extractInt(body, "world");
        if (worldId == null) worldId = 0;
        World world = Server.getInstance().getWorld(worldId);
        if (world == null) { respond(ex, 500, "{\"error\":\"World not found\"}"); return; }
        Character target = findOnlinePlayer(world, extractString(body, "characterName"), extractInt(body, "characterId"));
        if (target == null) { respond(ex, 400, "{\"error\":\"No online player found. Provide characterName/characterId of an ONLINE player.\"}"); return; }

        final Character t = target;
        TimerManager.getInstance().schedule(() -> {
            try {
                t.setLevel(255);
                t.updateStrDexIntLuk(Short.MAX_VALUE);
                t.updateMaxHpMaxMp(30000, 30000);
                t.setFame(13337);
                t.updateSingleStat(Stat.LEVEL, 255);
                t.updateSingleStat(Stat.FAME, 13337);
            } catch (Exception e) { log.warn("Admin API: maxstats failed for {}", t.getName(), e); }
        }, 0);
        log.info("Admin API: Maxed stats of {}", target.getName());
        respond(ex, 200, String.format("{\"success\":true,\"character\":\"%s\"}", target.getName().replace("\"", "\\\"")));
    }

    private void handleGiveSkill(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { respond(ex, 405, "{\"error\":\"Method not allowed\"}"); return; }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Integer skillId = extractInt(body, "skillId");
        Integer level = extractInt(body, "level");
        Integer worldId = extractInt(body, "world");
        if (skillId == null) { respond(ex, 400, "{\"error\":\"skillId is required\"}"); return; }
        if (worldId == null) worldId = 0;
        World world = Server.getInstance().getWorld(worldId);
        if (world == null) { respond(ex, 500, "{\"error\":\"World not found\"}"); return; }
        Character target = findOnlinePlayer(world, extractString(body, "characterName"), extractInt(body, "characterId"));
        if (target == null) { respond(ex, 400, "{\"error\":\"No online player found. Provide characterName/characterId of an ONLINE player.\"}"); return; }
        Skill skill = SkillFactory.getSkill(skillId);
        if (skill == null) { respond(ex, 400, String.format("{\"error\":\"Skill ID %d does not exist\"}", skillId)); return; }

        final Character t = target;
        final Skill sk = skill;
        final int lvl = (level != null && level > 0) ? Math.min(level, skill.getMaxLevel()) : skill.getMaxLevel();
        TimerManager.getInstance().schedule(() -> {
            try { t.changeSkillLevel(sk, (byte) lvl, sk.getMaxLevel(), -1); }
            catch (Exception e) { log.warn("Admin API: giveskill failed for {}", t.getName(), e); }
        }, 0);
        log.info("Admin API: Gave skill {} lvl {} to {}", skillId, lvl, target.getName());
        respond(ex, 200, String.format("{\"success\":true,\"character\":\"%s\",\"skillId\":%d,\"level\":%d}", target.getName().replace("\"", "\\\""), skillId, lvl));
    }

    private void handleCure(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { respond(ex, 405, "{\"error\":\"Method not allowed\"}"); return; }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Integer worldId = extractInt(body, "world");
        if (worldId == null) worldId = 0;
        World world = Server.getInstance().getWorld(worldId);
        if (world == null) { respond(ex, 500, "{\"error\":\"World not found\"}"); return; }
        Character target = findOnlinePlayer(world, extractString(body, "characterName"), extractInt(body, "characterId"));
        if (target == null) { respond(ex, 400, "{\"error\":\"No online player found. Provide characterName/characterId of an ONLINE player.\"}"); return; }

        final Character t = target;
        TimerManager.getInstance().schedule(() -> {
            try { t.dispelDebuffs(); } catch (Exception e) { log.warn("Admin API: cure failed for {}", t.getName(), e); }
        }, 0);
        log.info("Admin API: Cured debuffs of {}", target.getName());
        respond(ex, 200, String.format("{\"success\":true,\"character\":\"%s\"}", target.getName().replace("\"", "\\\"")));
    }

    private void handleSetGm(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { respond(ex, 405, "{\"error\":\"Method not allowed\"}"); return; }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Integer gmLevel = extractInt(body, "gmLevel");
        Integer worldId = extractInt(body, "world");
        if (gmLevel == null) { respond(ex, 400, "{\"error\":\"gmLevel is required (0-6)\"}"); return; }
        if (gmLevel < 0) gmLevel = 0;
        if (gmLevel > 6) gmLevel = 6;
        if (worldId == null) worldId = 0;
        World world = Server.getInstance().getWorld(worldId);
        if (world == null) { respond(ex, 500, "{\"error\":\"World not found\"}"); return; }
        Character target = findOnlinePlayer(world, extractString(body, "characterName"), extractInt(body, "characterId"));
        if (target == null) { respond(ex, 400, "{\"error\":\"No online player found. Provide characterName/characterId of an ONLINE player.\"}"); return; }

        final Character t = target;
        final int gl = gmLevel;
        TimerManager.getInstance().schedule(() -> {
            try { t.setGMLevel(gl); t.getClient().setGMLevel(gl); }
            catch (Exception e) { log.warn("Admin API: setgm failed for {}", t.getName(), e); }
        }, 0);
        log.info("Admin API: Set GM level of {} to {}", target.getName(), gmLevel);
        respond(ex, 200, String.format("{\"success\":true,\"character\":\"%s\",\"gmLevel\":%d}", target.getName().replace("\"", "\\\""), gmLevel));
    }

    private void handleHide(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { respond(ex, 405, "{\"error\":\"Method not allowed\"}"); return; }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Integer worldId = extractInt(body, "world");
        if (worldId == null) worldId = 0;
        World world = Server.getInstance().getWorld(worldId);
        if (world == null) { respond(ex, 500, "{\"error\":\"World not found\"}"); return; }
        Character target = findOnlinePlayer(world, extractString(body, "characterName"), extractInt(body, "characterId"));
        if (target == null) { respond(ex, 400, "{\"error\":\"No online player found. Provide characterName/characterId of an ONLINE player.\"}"); return; }

        final Character t = target;
        // Apply the GM Hide skill (9101004) — makes the target invisible to other players
        TimerManager.getInstance().schedule(() -> {
            try {
                Skill hide = SkillFactory.getSkill(9101004);
                if (hide != null) hide.getEffect(hide.getMaxLevel()).applyTo(t);
            } catch (Exception e) { log.warn("Admin API: hide failed for {}", t.getName(), e); }
        }, 0);
        log.info("Admin API: Hid {}", target.getName());
        respond(ex, 200, String.format("{\"success\":true,\"character\":\"%s\"}", target.getName().replace("\"", "\\\"")));
    }

    private void handleUnjail(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { respond(ex, 405, "{\"error\":\"Method not allowed\"}"); return; }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Integer worldId = extractInt(body, "world");
        if (worldId == null) worldId = 0;
        World world = Server.getInstance().getWorld(worldId);
        if (world == null) { respond(ex, 500, "{\"error\":\"World not found\"}"); return; }
        Character target = findOnlinePlayer(world, extractString(body, "characterName"), extractInt(body, "characterId"));
        if (target == null) { respond(ex, 400, "{\"error\":\"No online player found. Provide characterName/characterId of an ONLINE player.\"}"); return; }

        final Character t = target;
        final boolean wasJailed = target.getJailExpirationTimeLeft() > 0;
        TimerManager.getInstance().schedule(() -> {
            try { t.removeJailExpirationTime(); } catch (Exception e) { log.warn("Admin API: unjail failed for {}", t.getName(), e); }
        }, 0);
        log.info("Admin API: Unjailed {}", target.getName());
        respond(ex, 200, String.format("{\"success\":true,\"character\":\"%s\",\"wasJailed\":%b}", target.getName().replace("\"", "\\\""), wasJailed));
    }

    private void handleKillMobs(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { respond(ex, 405, "{\"error\":\"Method not allowed\"}"); return; }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Integer worldId = extractInt(body, "world");
        if (worldId == null) worldId = 0;
        World world = Server.getInstance().getWorld(worldId);
        if (world == null) { respond(ex, 500, "{\"error\":\"World not found\"}"); return; }
        Character target = findOnlinePlayer(world, extractString(body, "characterName"), extractInt(body, "characterId"));
        if (target == null) { respond(ex, 400, "{\"error\":\"No online player found. Provide characterName/characterId of an ONLINE player on the target map.\"}"); return; }

        final Character t = target;
        TimerManager.getInstance().schedule(() -> {
            try { t.getMap().killAllMonsters(); } catch (Exception e) { log.warn("Admin API: killmobs failed on map of {}", t.getName(), e); }
        }, 0);
        log.info("Admin API: Killed all mobs on map {} (via {})", target.getMap().getId(), target.getName());
        respond(ex, 200, String.format("{\"success\":true,\"mapId\":%d}", target.getMap().getId()));
    }

    private void handleClearDrops(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { respond(ex, 405, "{\"error\":\"Method not allowed\"}"); return; }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Integer worldId = extractInt(body, "world");
        if (worldId == null) worldId = 0;
        World world = Server.getInstance().getWorld(worldId);
        if (world == null) { respond(ex, 500, "{\"error\":\"World not found\"}"); return; }
        Character target = findOnlinePlayer(world, extractString(body, "characterName"), extractInt(body, "characterId"));
        if (target == null) { respond(ex, 400, "{\"error\":\"No online player found. Provide characterName/characterId of an ONLINE player on the target map.\"}"); return; }

        final Character t = target;
        TimerManager.getInstance().schedule(() -> {
            try { t.getMap().clearDrops(); } catch (Exception e) { log.warn("Admin API: cleardrops failed on map of {}", t.getName(), e); }
        }, 0);
        log.info("Admin API: Cleared drops on map {} (via {})", target.getMap().getId(), target.getName());
        respond(ex, 200, String.format("{\"success\":true,\"mapId\":%d}", target.getMap().getId()));
    }

    private void handleMapEffect(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { respond(ex, 405, "{\"error\":\"Method not allowed\"}"); return; }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String message = extractString(body, "message");
        Integer effectId = extractInt(body, "effectId");
        Integer worldId = extractInt(body, "world");
        if (message == null) message = "";
        final int fEffect = (effectId != null) ? effectId : 5120009; // default: a weather/snow effect
        if (worldId == null) worldId = 0;
        World world = Server.getInstance().getWorld(worldId);
        if (world == null) { respond(ex, 500, "{\"error\":\"World not found\"}"); return; }
        Character target = findOnlinePlayer(world, extractString(body, "characterName"), extractInt(body, "characterId"));
        if (target == null) { respond(ex, 400, "{\"error\":\"No online player found. Provide characterName/characterId of an ONLINE player on the target map.\"}"); return; }

        final Character t = target;
        final String fMsg = message;
        TimerManager.getInstance().schedule(() -> {
            try { t.getMap().startMapEffect(fMsg, fEffect); } catch (Exception e) { log.warn("Admin API: mapeffect failed on map of {}", t.getName(), e); }
        }, 0);
        log.info("Admin API: Map effect on map {} (via {}) effect={}", target.getMap().getId(), target.getName(), fEffect);
        respond(ex, 200, String.format("{\"success\":true,\"mapId\":%d,\"effectId\":%d}", target.getMap().getId(), fEffect));
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
