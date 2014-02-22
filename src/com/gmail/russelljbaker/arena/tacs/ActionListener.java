package com.gmail.russelljbaker.arena.tacs;

import java.io.PrintStream;

import mc.alk.arena.events.players.ArenaPlayerKillEvent;
import mc.alk.arena.objects.ArenaPlayer;
import mc.alk.arena.objects.events.ArenaEventHandler;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ActionListener
  implements Listener
{
  @EventHandler
  public void onQuit(PlayerQuitEvent e) {
    TacsArena.getArena().removePlayer(e.getPlayer());
    System.out.println("quit event handler");
  }

  @EventHandler
  public void onQuit(PlayerKickEvent e) {
    TacsArena.getArena().removePlayer(e.getPlayer());
    System.out.println("kick event handler");
  }

  @EventHandler
  public void testKill(ArenaPlayerKillEvent e) {
    System.out.println("Kill event handler");
  }
}