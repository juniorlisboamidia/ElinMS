import { NextRequest, NextResponse } from "next/server";

const GAME_API_URL = process.env.GAME_API_URL || "http://elin-ms-game.internal:8585";

// Raise an ONLINE player's level live (grants AP/SP/HP/MP naturally). Proxies to the Admin API.
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const { level, characterName, characterId, world = 0 } = body;

    if (level === undefined || level === null) {
      return NextResponse.json({ error: "level is required" }, { status: 400 });
    }
    if (!characterName && !characterId) {
      return NextResponse.json(
        { error: "Provide characterName or characterId of an online player" },
        { status: 400 },
      );
    }

    const res = await fetch(`${GAME_API_URL}/setlevel`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ level, characterName, characterId, world }),
    });

    const data = await res.json();
    if (!res.ok) {
      return NextResponse.json(data, { status: res.status });
    }
    return NextResponse.json(data);
  } catch (err: any) {
    return NextResponse.json(
      { error: "Failed to set level. Is the game server running?", details: err.message },
      { status: 500 },
    );
  }
}
