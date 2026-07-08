/*
    ElinMS — @shop command.
    Opens the GM Shop menu (categorias, tudo por 1 meso) via the gm_shop NPC script.
    Registered at rank 0 (@shop). Bump the rank in CommandsExecutor to make it GM-only.
*/
package client.command.commands.gm0;

import client.Client;
import client.command.Command;
import scripting.npc.NPCScriptManager;

public class ShopCommand extends Command {
    {
        setDescription("Open the GM Shop menu.");
    }

    @Override
    public void execute(Client c, String[] params) {
        // Starts server/scripts/npc/gm_shop.js (category menu -> opens shop 8000-8020).
        // Dialog speaker = 9010000 (Maple Administrator), a standard NPC present in
        // every v83 client (a custom NPC id here can crash the client's dialog box).
        NPCScriptManager.getInstance().start("gm_shop", c, 9010000, null);
    }
}
