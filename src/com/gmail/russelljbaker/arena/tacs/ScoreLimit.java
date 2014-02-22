package com.gmail.russelljbaker.arena.tacs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import mc.alk.arena.competition.match.Match;
import mc.alk.arena.controllers.messaging.MatchMessageHandler;
import mc.alk.arena.events.matches.MatchFindCurrentLeaderEvent;
import mc.alk.arena.events.players.ArenaPlayerKillEvent;
import mc.alk.arena.objects.ArenaPlayer;
import mc.alk.arena.objects.MatchParams;
import mc.alk.arena.objects.events.ArenaEventHandler;
import mc.alk.arena.objects.events.EventPriority;
import mc.alk.arena.objects.scoreboard.ArenaObjective;
import mc.alk.arena.objects.scoreboard.ArenaScoreboard;
import mc.alk.arena.objects.teams.ArenaTeam;
import mc.alk.arena.objects.victoryconditions.VictoryCondition;
import mc.alk.arena.objects.victoryconditions.interfaces.ScoreTracker;
import org.bukkit.entity.Player;

public class ScoreLimit extends VictoryCondition
  implements ScoreTracker
{
  final ArenaObjective score;
  public static Integer maxPoints = Integer.valueOf(-1);
  Map<ArenaTeam, Flag> teamFlags;
  MatchMessageHandler mmh;
  Object lock = new Object();
  HashMap<String, Integer> scores;
  String fileLoc = System.getProperty("user.dir") + "\\plugins\\ArenaTactics\\scores.txt";

  public ScoreLimit(Match match) {
    super(match);
    this.score = new ArenaObjective("playerKills", "Player Kills", 60);
    this.score.setDisplayName("&4First to " + maxPoints);
    readScores();
  }

  public void initScore(ArenaPlayer p, ArenaTeam t) {
    if ((this.scores != null) && (this.scores.containsKey(p.getDisplayName())))
    {
      this.score.setPoints(p, ((Integer)this.scores.get(p.getDisplayName())).intValue());
      this.score.setPoints(t, ((Integer)this.scores.get(p.getDisplayName())).intValue());
    }
  }

  public boolean addScore(ArenaTeam t, ArenaPlayer p) {
    return addScore(t, p, Integer.valueOf(1));
  }

  public int getScore(String s)
  {
    return 0;
  }
//  @ArenaEventHandler
//  public void playerKillEvent(ArenaPlayerKillEvent event) {
//    addScore(event.getTeam(), event.getPlayer());
//  }

  public synchronized boolean addScore(ArenaTeam t, ArenaPlayer p, Integer pts) {
    if ((this.score == null) || (t == null) || (p == null) || (pts == null)) {
      return false;
    }
    if (!this.scores.containsKey(p.getDisplayName()))
      this.scores.put(p.getDisplayName(), pts);
    else {
      this.scores.put(p.getDisplayName(), Integer.valueOf(((Integer)this.scores.get(p.getDisplayName())).intValue() + pts.intValue()));
    }
    this.score.setPoints(p, ((Integer)this.scores.get(p.getDisplayName())).intValue());
    this.score.setPoints(t, ((Integer)this.scores.get(p.getDisplayName())).intValue());

    writeScores();

    if (((Integer)this.scores.get(p.getDisplayName())).intValue() >= maxPoints.intValue()) {
      this.match.setVictor(t);
      return true;
    }

    return false;
  }

  private void writeScores()
  {
    try
    {
      File f = new File(this.fileLoc);
      if (f.exists()) {
        f.delete();
      }
      else {
        f.createNewFile();
      }

      FileOutputStream fileOut = new FileOutputStream(f);
      ObjectOutputStream out = new ObjectOutputStream(fileOut);
      out.writeObject(this.scores);
      out.close();
      fileOut.close();
    }
    catch (IOException io)
    {
      Tacs.getSelf().getLogger().warning(io.getStackTrace().toString());
    }
  }

  public void readScores()
  {
    try
    {
      File f = new File(this.fileLoc);
      if ((!f.exists()) || (f.length() == 0L))
      {
        f.createNewFile();
        this.scores = new HashMap();
        return;
      }

      FileInputStream fileIn = new FileInputStream(f);
      ObjectInputStream in = new ObjectInputStream(fileIn);
      this.scores = ((HashMap)in.readObject());
      in.close();
      fileIn.close();
    }
    catch (IOException i)
    {
      Tacs.getSelf().getLogger().warning(i.getStackTrace().toString());
    }
    catch (ClassNotFoundException e)
    {
      Tacs.getSelf().getLogger().warning(e.getStackTrace().toString());
    }
  }

  @ArenaEventHandler(priority=EventPriority.LOW)
  public void onFindCurrentLeader(MatchFindCurrentLeaderEvent event) {
    if (event.isMatchEnding()) {
      event.setResult(this.score.getMatchResult(this.match));
    } else {
      Collection leaders = this.score.getLeaders();
      if (leaders.size() > 1)
        event.setCurrentDrawers(leaders);
      else
        event.setCurrentLeaders(leaders);
    }
  }

  public List<ArenaTeam> getLeaders()
  {
    return this.score.getTeamLeaders();
  }

  public TreeMap<?, Collection<ArenaTeam>> getRanks()
  {
    return this.score.getRanks();
  }

  public void setDisplayTeams(boolean display)
  {
    this.score.setDisplayPlayers(true);
    this.score.setDisplayTeams(false);
  }

  public void setScoreBoard(ArenaScoreboard scoreboard)
  {
    this.score.setScoreBoard(scoreboard);
    scoreboard.addObjective(this.score);
  }

  public void setScore(int capturesToWin) {
    maxPoints = Integer.valueOf(capturesToWin);
  }

  public void setFlags(Map<ArenaTeam, Flag> teamFlags) {
    this.teamFlags = teamFlags;
  }

  public void setMessageHandler(MatchMessageHandler mmh) {
    this.mmh = mmh;
  }

  public String getScoreString() {
    Map map = new HashMap();
    map.put("{prefix}", this.match.getParams().getPrefix());
    String teamstr = this.mmh.getMessage("ArenaTactics.teamscore");
    String separator = this.mmh.getMessage("ArenaTactics.teamscore_separator");
    StringBuilder sb = new StringBuilder();
    List teams = this.match.getTeams();
    if (teams == null)
      return "";
    boolean first = true;
    for (int i = 0; i < teams.size(); i++) {
      if (!first) sb.append(separator);
      ArenaTeam t = (ArenaTeam)teams.get(i);
      Flag f = (Flag)this.teamFlags.get(t);
      Map map2 = new HashMap();
      map2.put("{team}", t.getDisplayName());
      map2.put("{captures}", this.score.getPoints(t));
      map2.put("{maxcaptures}", maxPoints);
      String holder = null;
      if (f.isHome()) {
        holder = this.mmh.getMessage("ArenaTactics.flaghome");
      } else if (!(f.getEntity() instanceof Player)) {
        holder = this.mmh.getMessage("ArenaTactics.flagfloor");
      } else {
        Player p = (Player)f.getEntity();
        holder = this.mmh.getMessage("ArenaTactics.flagperson") + p.getDisplayName();
      }
      map2.put("{flagholder}", holder);
      String str = this.mmh.format(teamstr, map2);
      sb.append(str);
      first = false;
    }
    map.put("{teamscores}", sb.toString());
    return this.mmh.getMessage("ArenaTactics.score", map);
  }

  public int GetMaxPoints() {
    return maxPoints.intValue();
  }
}