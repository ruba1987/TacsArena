package com.gmail.russelljbaker.arena.tacs;

import mc.alk.arena.BattleArena;
import mc.alk.arena.objects.victoryconditions.VictoryType;

import org.bukkit.plugin.java.JavaPlugin;

public final class Tacs extends JavaPlugin {
	public static Tacs plugin;
	
	
    @Override
    public void onEnable(){
    	plugin = this;
    	
    	loadConfig();	
		getLogger().info("[" + getName()+ "] v" + getDescription().getVersion()+ " enabled!");
		
		VictoryType.register(ScoreLimit.class, this);
    	BattleArena.registerCompetition(this, "TacsArena", "ta", TacsArena.class, new TacsExecutor());
    	
    }
 
    @Override
    public void onDisable() {
		getLogger().info("[" + getName()+ "] v" + getDescription().getVersion()+ " disabled!");
    }
    
    @Override
    public void reloadConfig(){
    	super.reloadConfig();
    	loadConfig();
    }
    
    private void loadConfig(){
    }
    
	public static Tacs getSelf() {
		return plugin;
	}
	
}