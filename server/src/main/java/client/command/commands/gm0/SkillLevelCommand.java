/*
    ElinMS — @skill: define o nivel de uma skill de uma vez (contorna o
    "1 ponto por vez" da janela de habilidades do cliente v83).
    Uso: @skill <skillId> [nivel]  (nivel padrao = maximo da skill)
*/
package client.command.commands.gm0;

import client.Character;
import client.Client;
import client.Skill;
import client.SkillFactory;
import client.command.Command;

public class SkillLevelCommand extends Command {
    {
        setDescription("Define o nivel de uma skill. Uso: @skill <skillId> [nivel]");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character chr = c.getPlayer();
        if (params.length < 1) {
            chr.yellowMessage("Uso: @skill <skillId> [nivel]. Sem o nivel, sobe ao maximo. Ex: @skill 2001004 20");
            return;
        }

        int skillId;
        try {
            skillId = Integer.parseInt(params[0]);
        } catch (NumberFormatException e) {
            chr.yellowMessage("skillId invalido.");
            return;
        }

        Skill skill = SkillFactory.getSkill(skillId);
        if (skill == null) {
            chr.yellowMessage("Skill " + skillId + " nao existe.");
            return;
        }

        int maxLevel = skill.getMaxLevel();
        int level = maxLevel;
        if (params.length >= 2) {
            try {
                level = Integer.parseInt(params[1]);
            } catch (NumberFormatException e) {
                chr.yellowMessage("Nivel invalido.");
                return;
            }
        }
        if (level < 0) {
            level = 0;
        } else if (level > maxLevel) {
            level = maxLevel;
        }

        chr.changeSkillLevel(skill, (byte) level, maxLevel, -1);
        chr.dropMessage(6, "Skill " + skillId + " definida no nivel " + level + " (max " + maxLevel + ").");
    }
}
