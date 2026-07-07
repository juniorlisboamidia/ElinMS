import { NextRequest, NextResponse } from "next/server";

const GAME_API_URL = process.env.GAME_API_URL || "http://elin-ms-game.internal:8585";

// Fully restore an ONLINE player's HP/MP, live. Proxies to the Admin API.
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

    const res = await fetch(`${GAME_API_URL}/heal`, {
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
      { error: "Failed to heal. Is the game server running?", details: err.message },
      { status: 500 },
    );
  }
}
