/*
    ElinMS — @scroll: aplica um pergaminho em massa no item equipado.
    Faz o lote inteiro numa acao so (o cliente v83 so deixa scrollar 1x por
    animacao; isto contorna esse limite server-side). Usa White Scroll para
    proteger o slot em caso de FALHA, se houver no inventario.
*/
package client.command.commands.gm0;

import client.Character;
import client.Client;
import client.command.Command;
import client.inventory.Equip;
import client.inventory.Equip.ScrollResult;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.ModifyInventory;
import client.inventory.manipulator.InventoryManipulator;
import constants.id.ItemId;
import constants.inventory.ItemConstants;
import server.ItemInformationProvider;
import tools.PacketCreator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ScrollAllCommand extends Command {
    {
        setDescription("Aplica um pergaminho em massa no item equipado. Uso: @scroll <qtd> [slot]");
    }

    // slot equipado (posicao negativa) por nome amigavel
    private static final Map<String, Integer> SLOTS = Map.ofEntries(
            Map.entry("weapon", -11), Map.entry("arma", -11),
            Map.entry("hat", -1), Map.entry("chapeu", -1),
            Map.entry("top", -5), Map.entry("overall", -5), Map.entry("roupa", -5),
            Map.entry("bottom", -6), Map.entry("calca", -6),
            Map.entry("shoes", -7), Map.entry("sapato", -7),
            Map.entry("gloves", -8), Map.entry("luva", -8),
            Map.entry("cape", -9), Map.entry("capa", -9),
            Map.entry("shield", -10), Map.entry("escudo", -10),
            Map.entry("earring", -4), Map.entry("brinco", -4),
            Map.entry("eye", -3), Map.entry("face", -2),
            Map.entry("pendant", -17), Map.entry("pingente", -17));

    @Override
    public void execute(Client c, String[] params) {
        Character chr = c.getPlayer();
        if (params.length < 1) {
            chr.yellowMessage("Uso: @scroll <qtd> [slot]  (slot padrao: weapon). Ex: @scroll 50  |  @scroll 20 gloves");
            chr.yellowMessage("Coloque o pergaminho no inventario USE e equipe o item. White Scroll protege o slot na falha.");
            return;
        }

        int count;
        try {
            count = Integer.parseInt(params[0]);
        } catch (NumberFormatException e) {
            chr.yellowMessage("Quantidade invalida. Ex: @scroll 50");
            return;
        }
        if (count < 1) {
            chr.yellowMessage("A quantidade tem que ser no minimo 1.");
            return;
        }
        count = Math.min(count, 500); // teto de seguranca

        int equipSlot = -11; // weapon por padrao
        if (params.length >= 2) {
            Integer s = SLOTS.get(params[1].toLowerCase());
            if (s == null) {
                chr.yellowMessage("Slot desconhecido: " + params[1] + ". Use: weapon, hat, top, overall, bottom, shoes, gloves, cape, shield, earring, eye, face, pendant.");
                return;
            }
            equipSlot = s;
        }

        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        Inventory equipped = chr.getInventory(InventoryType.EQUIPPED);
        Inventory useInv = chr.getInventory(InventoryType.USE);

        Equip target = (Equip) equipped.getItem((short) equipSlot);
        if (target == null) {
            chr.yellowMessage("Nao ha nada equipado nesse slot (" + (params.length >= 2 ? params[1] : "weapon") + ").");
            return;
        }

        // escolhe o pergaminho: primeiro item do USE aplicavel ao alvo (ignora White Scroll)
        int scrollId = -1;
        for (Item it : useInv.list()) {
            if (it.getItemId() == ItemId.WHITE_SCROLL) {
                continue;
            }
            if (canScrollTo(ii, it.getItemId(), target.getItemId())) {
                scrollId = it.getItemId();
                break;
            }
        }
        if (scrollId < 0) {
            chr.yellowMessage("Nenhum pergaminho no USE serve para o item equipado nesse slot.");
            return;
        }

        final boolean cleanslate = ItemConstants.isCleanSlate(scrollId);
        final boolean modifier = ItemConstants.isModifierScroll(scrollId);

        int success = 0, fail = 0, curse = 0, scrollsUsed = 0, whitesUsed = 0;
        boolean cursed = false;

        c.lockClient();
        try {
            for (int i = 0; i < count; i++) {
                Item scroll = useInv.findById(scrollId);
                if (scroll == null || scroll.getQuantity() < 1) {
                    break; // acabaram os pergaminhos
                }
                Equip toScroll = (Equip) equipped.getItem((short) equipSlot);
                if (toScroll == null) {
                    break; // item sumiu
                }
                if (!modifier && !cleanslate && toScroll.getUpgradeSlots() < 1) {
                    break; // sem slots de upgrade restantes
                }

                Item white = cleanslate ? null : useInv.findById(ItemId.WHITE_SCROLL);
                boolean useWhite = white != null && white.getQuantity() > 0;

                byte oldLevel = toScroll.getLevel();
                byte oldSlots = toScroll.getUpgradeSlots();

                Equip scrolled = (Equip) ii.scrollEquipWithId(toScroll, scrollId, useWhite, 0, chr.isGM());
                ScrollResult result;
                if (scrolled == null) {
                    result = ScrollResult.CURSE;
                } else if (scrolled.getLevel() > oldLevel
                        || (cleanslate && scrolled.getUpgradeSlots() == oldSlots + 1)
                        || ItemConstants.isFlagModifier(scrollId, scrolled.getFlag())) {
                    result = ScrollResult.SUCCESS;
                } else {
                    result = ScrollResult.FAIL;
                }

                // consome 1 pergaminho
                InventoryManipulator.removeFromSlot(c, InventoryType.USE, scroll.getPosition(), (short) 1, false);
                scrollsUsed++;

                // White Scroll: consumido so quando protege (na falha)
                if (result == ScrollResult.FAIL && useWhite) {
                    Item w = useInv.findById(ItemId.WHITE_SCROLL);
                    if (w != null && w.getQuantity() > 0) {
                        InventoryManipulator.removeFromSlot(c, InventoryType.USE, w.getPosition(), (short) 1, false);
                        whitesUsed++;
                    }
                }

                if (result == ScrollResult.SUCCESS) {
                    success++;
                } else if (result == ScrollResult.CURSE) {
                    curse++;
                    cursed = true;
                    // item destruido pelo curse: desequipa e remove
                    equipped.lockInventory();
                    try {
                        chr.unequippedItem(toScroll);
                        equipped.removeItem(toScroll.getPosition());
                    } finally {
                        equipped.unlockInventory();
                    }
                    List<ModifyInventory> del = new ArrayList<>();
                    del.add(new ModifyInventory(3, toScroll));
                    c.sendPacket(PacketCreator.modifyInventory(true, del));
                    chr.getMap().broadcastMessage(PacketCreator.getScrollEffect(chr.getId(), ScrollResult.CURSE, false, false));
                    break;
                } else {
                    fail++;
                }
            }

            // refresca o item equipado no cliente (se sobreviveu)
            if (!cursed) {
                Equip finalEq = (Equip) equipped.getItem((short) equipSlot);
                if (finalEq != null) {
                    List<ModifyInventory> mods = new ArrayList<>();
                    mods.add(new ModifyInventory(3, finalEq));
                    mods.add(new ModifyInventory(0, finalEq));
                    c.sendPacket(PacketCreator.modifyInventory(true, mods));
                    chr.equipChanged();
                }
            }
        } finally {
            c.unlockClient();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Scroll em massa: ").append(scrollsUsed).append(" tentativa(s) — ")
          .append(success).append(" sucesso(s), ").append(fail).append(" falha(s)");
        if (curse > 0) {
            sb.append(", ").append(curse).append(" curse (item destruido)");
        }
        if (whitesUsed > 0) {
            sb.append(". White Scrolls usados: ").append(whitesUsed);
        }
        sb.append(".");
        chr.dropMessage(6, sb.toString());
    }

    // Reproduz a checagem de compatibilidade do ScrollHandler (categoria/chaos/clean slate/modificador).
    private static boolean canScrollTo(ItemInformationProvider ii, int scrollId, int itemId) {
        if (ItemConstants.isCleanSlate(scrollId) || ItemConstants.isChaosScroll(scrollId) || ItemConstants.isModifierScroll(scrollId)) {
            return true;
        }
        List<Integer> reqs = ii.getScrollReqs(scrollId);
        if (reqs != null && !reqs.isEmpty()) {
            return reqs.contains(itemId);
        }
        int sid = scrollId / 100;
        if (sid == 20492) { // scroll para acessorio (pingente, cinto, anel)
            return true;
        }
        return (scrollId / 100) % 100 == (itemId / 10000) % 100;
    }
}
