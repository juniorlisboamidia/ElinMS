import { NextRequest, NextResponse } from "next/server";

const GAME_API_URL = process.env.GAME_API_URL || "http://elin-ms-game.internal:8585";

// Reset an ONLINE player's AP live (STR/DEX/INT/LUK back to class base, all points
// returned as free AP). Proxies to the Admin API. Works on the in-memory character,
// unlike update_character which only writes the DB and gets overwritten by autosave.
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const { characterName, characterId, world = 0 } = body;

    if (!characterName && !characterId) {
      return NextResponse.json(
        { error: "Provide characterName or characterId of an online player" },
        { status: 400 },
      );
    }

    const res = await fetch(`${GAME_API_URL}/resetstats`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ characterName, characterId, world }),
    });

    const data = await res.json();
    if (!res.ok) {
      return NextResponse.json(data, { status: res.status });
    }
    return NextResponse.json(data);
  } catch (err: any) {
    return NextResponse.json(
      { error: "Failed to reset stats. Is the game server running?", details: err.message },
      { status: 500 },
    );
  }
}
