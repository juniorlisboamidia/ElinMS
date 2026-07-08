import { NextRequest, NextResponse } from "next/server";

const GAME_API_URL = process.env.GAME_API_URL || "http://elin-ms-game.internal:8585";

// Set an ONLINE player's STR/DEX/INT/LUK (and optionally free AP, max HP, max MP) live.
// Any omitted stat keeps its current value. Proxies to the Admin API. Applies to the
// in-memory character, unlike update_character which only writes the DB (overwritten online).
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const { str, dex, int: intStat, luk, ap, maxhp, maxmp, characterName, characterId, world = 0 } = body;

    if (!characterName && !characterId) {
      return NextResponse.json(
        { error: "Provide characterName or characterId of an online player" },
        { status: 400 },
      );
    }
    if ([str, dex, intStat, luk, ap, maxhp, maxmp].every((v) => v === undefined || v === null)) {
      return NextResponse.json(
        { error: "Provide at least one of: str, dex, int, luk, ap, maxhp, maxmp" },
        { status: 400 },
      );
    }

    const res = await fetch(`${GAME_API_URL}/setstats`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ str, dex, int: intStat, luk, ap, maxhp, maxmp, characterName, characterId, world }),
    });

    const data = await res.json();
    if (!res.ok) {
      return NextResponse.json(data, { status: res.status });
    }
    return NextResponse.json(data);
  } catch (err: any) {
    return NextResponse.json(
      { error: "Failed to set stats. Is the game server running?", details: err.message },
      { status: 500 },
    );
  }
}
