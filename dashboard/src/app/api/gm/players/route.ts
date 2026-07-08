import { NextResponse } from "next/server";

const GAME_API_URL = process.env.GAME_API_URL || "http://elin-ms-game.internal:8585";

// List the players currently ONLINE (roster). Proxies to the game Admin API.
export async function GET() {
  try {
    const res = await fetch(`${GAME_API_URL}/players`);
    const data = await res.json();
    if (!res.ok) {
      return NextResponse.json(data, { status: res.status });
    }
    return NextResponse.json(data);
  } catch (err: any) {
    return NextResponse.json(
      { error: "Failed to list players. Is the game server running?", details: err.message },
      { status: 500 },
    );
  }
}
