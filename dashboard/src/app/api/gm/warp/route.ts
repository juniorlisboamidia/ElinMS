import { NextRequest, NextResponse } from "next/server";

const GAME_API_URL = process.env.GAME_API_URL || "http://elin-ms-game.internal:8585";

// Warp an ONLINE player to a map, live (no relog). Proxies to the game Admin API.
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const { characterName, characterId, mapId, world = 0 } = body;

    if (mapId === undefined || mapId === null) {
      return NextResponse.json({ error: "mapId is required" }, { status: 400 });
    }
    if (!characterName && !characterId) {
      return NextResponse.json(
        { error: "Provide characterName or characterId of an online player" },
        { status: 400 },
      );
    }

    const res = await fetch(`${GAME_API_URL}/warp`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ characterName, characterId, mapId, world }),
    });

    const data = await res.json();
    if (!res.ok) {
      return NextResponse.json(data, { status: res.status });
    }
    return NextResponse.json(data);
  } catch (err: any) {
    return NextResponse.json(
      { error: "Failed to warp. Is the game server running?", details: err.message },
      { status: 500 },
    );
  }
}
