package com.gmail.russelljbaker.arena.tacs;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.bukkit.entity.Player;

import mc.alk.arena.competition.match.Match;
import mc.alk.arena.controllers.messaging.MatchMessageHandler;
import mc.alk.arena.events.matches.MatchFindCurrentLeaderEvent;
import mc.alk.arena.events.players.ArenaPlayerKillEvent;
import mc.alk.arena.objects.ArenaPlayer;
import mc.alk.arena.objects.events.ArenaEventHandler;
import mc.alk.arena.objects.events.EventPriority;
import mc.alk.arena.objects.scoreboard.ArenaObjective;
import mc.alk.arena.objects.scoreboard.ArenaScoreboard;
import mc.alk.arena.objects.teams.ArenaTeam;
import mc.alk.arena.objects.victoryconditions.VictoryCondition;
import mc.alk.arena.objects.victoryconditions.interfaces.ScoreTracker;

public class ScoreLimit extends VictoryCondition implements ScoreTracker{
	final ArenaObjective score;
	//final StartController sc;
	public static Integer maxPoints = -1;
	Map<ArenaTeam, Flag> teamFlags;
	MatchMessageHandler mmh;
	HashMap<String, Integer> scores = new HashMap<>();
	
	public ScoreLimit(Match match) {
		super(match);
		score = new ArenaObjective("playerKills",  "Player Kills", 60);
		score.setDisplayName("&4First to " + maxPoints);
	}
	
	public boolean addScore(ArenaTeam t, ArenaPlayer p){
		return addScore(t, p, 1);
	}
	

	public int getScore(String s) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	@ArenaEventHandler
	public void playerKillEvent(ArenaPlayerKillEvent event) {
		addScore(event.getTeam(), event.getPlayer());
	}
	
	public boolean addScore(ArenaTeam t, ArenaPlayer p, Integer pts) {

		if(score == null || t == null || p == null || pts == null)
			return false;
		score.addPoints(p, pts);
		score.addPoints(t, pts);
		if(!scores.containsKey(p.getDisplayName()))
			scores.put(p.getDisplayName(), pts);
		else
			scores.put(p.getDisplayName(), scores.get(p.getDisplayName()) + pts);
		if (scores.get(p.getDisplayName()) >= maxPoints){
			this.match.setVictor(t);
			return true;
		}
		
		return false;
		
	}
	
	@ArenaEventHandler(priority = EventPriority.LOW)
	public void onFindCurrentLeader(MatchFindCurrentLeaderEvent event) {
		if (event.isMatchEnding()){
			event.setResult(score.getMatchResult(match));
		} else {
			Collection<ArenaTeam> leaders = score.getLeaders();
			if (leaders.size() > 1){
				event.setCurrentDrawers(leaders);
			} else {
				event.setCurrentLeaders(leaders);
			}
		}
	}

	@Override
	public List<ArenaTeam> getLeaders() {
		return score.getTeamLeaders();
	}

	@Override
	public TreeMap<?, Collection<ArenaTeam>> getRanks() {
		return score.getRanks();
	}

	@Override
	public void setDisplayTeams(boolean display) {
		score.setDisplayPlayers(display);
	}

	@Override
	public void setScoreBoard(ArenaScoreboard scoreboard) {
		this.score.setScoreBoard(scoreboard);
		scoreboard.addObjective(score);
	}

	public void setScore(int capturesToWin) {
		maxPoints = capturesToWin;
	}
	
	public void setFlags(Map<ArenaTeam, Flag> teamFlags){
		this.teamFlags = teamFlags;
	}
	
	public void setMessageHandler(MatchMessageHandler mmh){
		this.mmh = mmh;
	}

	public String getScoreString() {
		Map<String,String> map = new HashMap<String,String>();
		map.put("{prefix}", match.getParams().getPrefix());
		String teamstr = mmh.getMessage("CaptureTheFlag.teamscore");
		String separator = mmh.getMessage("CaptureTheFlag.teamscore_separator");
		StringBuilder sb = new StringBuilder();
		List<ArenaTeam> teams = match.getTeams();
		if (teams == null)
			return "";
		boolean first = true;
		for (int i=0;i<teams.size();i++){
			if (!first) sb.append(separator);
			ArenaTeam t = teams.get(i);
			Flag f = teamFlags.get(t);
			Map<String,String> map2 = new HashMap<String,String>();
			map2.put("{team}", t.getDisplayName());
			map2.put("{captures}",score.getPoints(t)+"");
			map2.put("{maxcaptures}",maxPoints+"");
			String holder = null;
			if (f.isHome()){
				holder = mmh.getMessage("CaptureTheFlag.flaghome");
			} else if (!(f.getEntity() instanceof Player)){
				holder = mmh.getMessage("CaptureTheFlag.flagfloor");
			} else {
				Player p = (Player) f.getEntity();
				holder = mmh.getMessage("CaptureTheFlag.flagperson") + p.getDisplayName();
			}
			map2.put("{flagholder}",holder);
			String str = mmh.format(teamstr,map2);
			sb.append(str);
			first = false;
		}
		map.put("{teamscores}",sb.toString());
		return mmh.getMessage("CaptureTheFlag.score",map);
	}

	public int GetMaxPoints() {
		return maxPoints;
	}



}
