import { NextRequest, NextResponse } from "next/server";

const GAME_API_URL = process.env.GAME_API_URL || "http://elin-ms-game.internal:8585";

// Send a private on-screen message to a single ONLINE player, live. Proxies to the Admin API.
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const { message, type, characterName, characterId, world = 0 } = body;

    if (!message) {
      return NextResponse.json({ error: "message is required" }, { status: 400 });
    }
    if (!characterName && !characterId) {
      return NextResponse.json(
        { error: "Provide characterName or characterId of an online player" },
        { status: 400 },
      );
    }

    const res = await fetch(`${GAME_API_URL}/msgplayer`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ message, type, characterName, characterId, world }),
    });

    const data = await res.json();
    if (!res.ok) {
      return NextResponse.json(data, { status: res.status });
    }
    return NextResponse.json(data);
  } catch (err: any) {
    return NextResponse.json(
      { error: "Failed to message player. Is the game server running?", details: err.message },
      { status: 500 },
    );
  }
}
