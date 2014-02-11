package com.gmail.russelljbaker.arena.tacs;

import java.util.logging.Logger;
import mc.alk.arena.BattleArena;
import mc.alk.arena.objects.victoryconditions.VictoryType;
import org.bukkit.Server;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

public final class Tacs extends JavaPlugin
{
  public static Tacs plugin;
  public static Server s;

  public void onEnable()
  {
    plugin = this;

    loadConfig();
    getLogger().info("[" + getName() + "] v" + getDescription().getVersion() + " enabled!");

    VictoryType.register(ScoreLimit.class, this);
    BattleArena.registerCompetition(this, "TacsArena", "ta", TacsArena.class, new TacsExecutor());

    s = getServer();
    
    s.getPluginManager().registerEvents(new ActionListener(), this);
  }

  public static Server getServerInstance()
  {
    return s;
  }

  public void onDisable()
  {
    getLogger().info("[" + getName() + "] v" + getDescription().getVersion() + " disabled!");
  }

  public void reloadConfig()
  {
    super.reloadConfig();
    loadConfig();
  }

  private void loadConfig() {
  }

  public static Tacs getSelf() {
    return plugin;
  }
}