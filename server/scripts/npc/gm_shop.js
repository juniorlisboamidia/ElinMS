/*
 * ElinMS — @shop (GM Shop), menu por CLASSE (3 niveis).
 * @shop -> Classe -> sub-tipo -> (pagina, se >1) -> abre a loja com os itens por 1 meso.
 * Classes mostram equip restrito aquela classe (reqJob) + as armas da classe.
 * Common = armadura sem restricao + acessorios. Use/Scroll por sub-tipo.
 * Itens de CASH sao excluidos. Lojas: shopid 8000-8094, npcid 9010000. Geradas por script.
 */

// [cat, [ [sub, [shopid, ...]], ... ]]
var CATS = [
  ["Warrior", [["Hat",[8000]],["Top",[8001]],["Overall",[8002]],["Bottom",[8003]],["Shoes",[8004]],["Gloves",[8005]],["Shield",[8006]],["1H Sword",[8007,8008]],["2H Sword",[8009]],["1H Mace",[8010]],["2H Mace",[8011]],["Spear",[8012]],["Polearm",[8013]],["1H Axe",[8014]],["2H Axe",[8015]]]],
  ["Mage", [["Hat",[8016]],["Top",[8017]],["Overall",[8018]],["Bottom",[8019]],["Shoes",[8020]],["Gloves",[8021]],["Shield",[8022]],["Staffs",[8023]],["Wands",[8024]]]],
  ["Archer", [["Hat",[8025]],["Top",[8026]],["Overall",[8027]],["Bottom",[8028]],["Shoes",[8029]],["Gloves",[8030]],["Bow",[8031]],["Crossbow",[8032]]]],
  ["Thief", [["Hat",[8033]],["Top",[8034]],["Overall",[8035]],["Bottom",[8036]],["Shoes",[8037]],["Gloves",[8038]],["Shield",[8039]],["Claw",[8040]],["Dagger",[8041]]]],
  ["Pirate", [["Hat",[8042]],["Overall",[8043]],["Shoes",[8044]],["Gloves",[8045]],["Gun",[8046]],["Knuckle",[8047]]]],
  ["Common", [["Hat",[8048,8049]],["Top",[8050]],["Overall",[8051]],["Bottom",[8052]],["Shoes",[8053]],["Gloves",[8054]],["Shield",[8055]],["Earrings",[8056]],["Eye",[8057]],["Face",[8058]],["Cape",[8059]],["Pendants",[8060]],["Rings",[8061]],["Belt",[8062]],["Medal",[8063,8064]]]],
  ["Use", [["Potions",[8065]],["Arrows",[8066]],["Stars",[8067]],["Bullets",[8068]],["Skill Book",[8069,8070]],["Etc",[8071,8072,8073,8074,8075,8076,8077,8078,8079,8080,8081,8082,8083]]]],
  ["Scroll", [["Especiais (Chaos/White)",[8084]],["Armour",[8085,8086,8087,8088]],["Weapon",[8089,8090,8091]],["Accessory",[8092,8093]],["Pet Equip",[8094]],["Misc",[8095]]]],
  ["Chairs", [["Todas as Cadeiras",[8096]]]]
];

var state, selCat, selSub;

function start() {
  state = 0; selCat = -1; selSub = -1;
  action(1, 0, 0);
}

function action(mode, type, selection) {
  if (mode == -1) { cm.dispose(); return; }

  if (state == 0) { showCats(); state = 1; return; }

  if (state == 1) {
    if (mode != 1 || selection < 0 || selection >= CATS.length) { cm.dispose(); return; }
    selCat = selection;
    showSubs(selCat);
    state = 2;
    return;
  }

  if (state == 2) {
    var subs = CATS[selCat][1];
    if (mode != 1 || selection < 0 || selection >= subs.length) { cm.dispose(); return; }
    selSub = selection;
    var shopids = subs[selSub][1];
    if (shopids.length == 1) {
      cm.openShopNPC(shopids[0]);
      cm.dispose();
    } else {
      showPages(shopids);
      state = 3;
    }
    return;
  }

  if (state == 3) {
    var sids = CATS[selCat][1][selSub][1];
    if (mode != 1 || selection < 0 || selection >= sids.length) { cm.dispose(); return; }
    cm.openShopNPC(sids[selection]);
    cm.dispose();
    return;
  }

  cm.dispose();
}

function showCats() {
  var text = "#eGM Shop#n\r\n\r\nTudo por #r1 meso#k! Escolha:\r\n\r\n#b";
  for (var i = 0; i < CATS.length; i++) text += "#L" + i + "#" + CATS[i][0] + "#l\r\n";
  cm.sendSimple(text);
}

function showSubs(ci) {
  var subs = CATS[ci][1];
  var text = "#e" + CATS[ci][0] + "#n\r\n\r\nEscolha (tudo por #r1 meso#k):\r\n\r\n#b";
  for (var i = 0; i < subs.length; i++) {
    var n = subs[i][1].length;
    text += "#L" + i + "#" + subs[i][0] + (n > 1 ? (" (" + n + " pags)") : "") + "#l\r\n";
  }
  cm.sendSimple(text);
}

function showPages(shopids) {
  var text = "#eEscolha a pagina#n\r\n\r\n#b";
  for (var i = 0; i < shopids.length; i++) text += "#L" + i + "#Pagina " + (i + 1) + "#l\r\n";
  cm.sendSimple(text);
}
