/*
    ElinMS — @check: avalia a "qualidade" (tier estilo Godly Items / MapleRoyals) dos
    itens equipados, comparando os stats atuais com a media (base do item no WZ).
    Substituto server-side do glow colorido, e util pra confirmar se um item ficou godly.
    Tiers por desvio total vs base:
      <= -1  CINZA | 0..+5 BRANCO (sem scroll) / LARANJA (scrollado)
      +6..+22 AZUL | +23..+39 ROXO | >= +40 AMARELO
*/
package client.command.commands.gm0;

import client.Character;
import client.Client;
import client.command.Command;
import client.inventory.Equip;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.ItemInformationProvider;

import java.util.Map;

public class GodlyCheckCommand extends Command {
    {
        setDescription("Avalia a qualidade (tier) dos itens equipados. Uso: @check");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character chr = c.getPlayer();
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        chr.dropMessage(6, "=== Qualidade dos itens equipados (desvio total vs. base) ===");
        boolean any = false;
        for (Item it : chr.getInventory(InventoryType.EQUIPPED).list()) {
            if (!(it instanceof Equip eq)) {
                continue;
            }
            Map<String, Integer> base = ii.getEquipStats(it.getItemId());
            if (base == null) {
                continue;
            }
            int dev = 0;
            dev += eq.getStr()   - base.getOrDefault("STR", 0);
            dev += eq.getDex()   - base.getOrDefault("DEX", 0);
            dev += eq.getInt()   - base.getOrDefault("INT", 0);
            dev += eq.getLuk()   - base.getOrDefault("LUK", 0);
            dev += eq.getWatk()  - base.getOrDefault("PAD", 0);
            dev += eq.getMatk()  - base.getOrDefault("MAD", 0);
            dev += eq.getWdef()  - base.getOrDefault("PDD", 0);
            dev += eq.getMdef()  - base.getOrDefault("MDD", 0);
            dev += eq.getAcc()   - base.getOrDefault("ACC", 0);
            dev += eq.getAvoid() - base.getOrDefault("EVA", 0);
            dev += eq.getSpeed() - base.getOrDefault("Speed", 0);
            dev += eq.getJump()  - base.getOrDefault("Jump", 0);
            dev += eq.getHp()    - base.getOrDefault("MHP", 0);
            dev += eq.getMp()    - base.getOrDefault("MMP", 0);

            boolean scrolled = eq.getLevel() > 0;
            String name = ii.getName(it.getItemId());
            chr.dropMessage(6, (name != null ? name : "#" + it.getItemId())
                    + ": " + (dev >= 0 ? "+" : "") + dev + " -> " + tierOf(dev, scrolled));
            any = true;
        }
        if (!any) {
            chr.dropMessage(6, "Nenhum equipamento com stats para avaliar.");
        }
    }

    private static String tierOf(int dev, boolean scrolled) {
        if (dev <= -1) {
            return "CINZA";
        }
        if (dev <= 5) {
            return scrolled ? "LARANJA" : "BRANCO";
        }
        if (dev <= 22) {
            return "AZUL";
        }
        if (dev <= 39) {
            return "ROXO";
        }
        return "AMARELO";
    }
}
