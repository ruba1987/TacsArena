package com.gmail.russelljbaker.arena.tacs;

import mc.alk.arena.BattleArena;
import mc.alk.arena.executors.CustomCommandExecutor;
import mc.alk.arena.executors.MCCommand;
import mc.alk.arena.objects.ArenaPlayer;
import mc.alk.arena.objects.MatchParams;
import mc.alk.arena.objects.arenas.Arena;

import org.bukkit.command.CommandSender;

public class TacsExecutor extends CustomCommandExecutor {
	
	public TacsExecutor(){
		super();
	}
	@MCCommand(cmds={"joinGame"}, inGame=true, admin=true)
	public boolean joinGame(ArenaPlayer sender, MatchParams mp, Arena arena)
	{
		TacsArena tacsArena = (TacsArena)arena;
		tacsArena.addPlayer(sender, arena, mp);
		return true;
	}
	
	@MCCommand(cmds={"addFlag"}, inGame=true, admin=true)
	public boolean addFlag(ArenaPlayer sender, Arena arena, Integer index) {
		if (!(arena instanceof TacsArena)){
			return sendMessage(sender,"&eArena " + arena.getName() +" is not a A Tacs arena!");
		}
		if (index < 1 || index > 100){
			return sendMessage(sender,"&2index must be between [1-100]!");}

		TacsArena tacs = (TacsArena) arena;
		tacs.addFlag(index -1, sender.getLocation());
		BattleArena.saveArenas(Tacs.getSelf());
		return sendMessage(sender,"&2Team &6"+index+"&2 flag added!");
	}

	@MCCommand(cmds={"clearFlags"}, admin=true)
	public boolean clearFlags(CommandSender sender, Arena arena) {
		if (!(arena instanceof TacsArena)){
			return sendMessage(sender,"&eArena " + arena.getName() +" is not a Tacs arena!");
		}
		TacsArena tacs = (TacsArena) arena;
		tacs.clearFlags();
		return sendMessage(sender,"&2Flags cleared for &6"+arena.getName());
	}
}
