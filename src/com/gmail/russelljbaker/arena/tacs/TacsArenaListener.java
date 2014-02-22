package com.gmail.russelljbaker.arena.tacs;

import mc.alk.arena.events.players.ArenaPlayerKillEvent;
import mc.alk.arena.objects.arenas.ArenaListener;
import mc.alk.arena.objects.events.ArenaEventHandler;

public class TacsArenaListener implements ArenaListener{
	  @ArenaEventHandler
	  public void testKillAlt(ArenaPlayerKillEvent e) {
		  System.out.println("Other Kill event arena handler");
	  }
}
