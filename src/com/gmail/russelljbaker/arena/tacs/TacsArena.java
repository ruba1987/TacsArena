package com.gmail.russelljbaker.arena.tacs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import mc.alk.arena.BattleArena;
import mc.alk.arena.competition.match.Match;
import mc.alk.arena.controllers.BattleArenaController;
import mc.alk.arena.controllers.PlayerStoreController;
import mc.alk.arena.controllers.TeamController;
import mc.alk.arena.controllers.messaging.MatchMessageHandler;
import mc.alk.arena.events.players.ArenaPlayerKillEvent;
import mc.alk.arena.events.players.ArenaPlayerTeleportEvent;
import mc.alk.arena.executors.BAExecutor;
import mc.alk.arena.objects.ArenaPlayer;
import mc.alk.arena.objects.MatchParams;
import mc.alk.arena.objects.MatchState;
import mc.alk.arena.objects.arenas.Arena;
import mc.alk.arena.objects.arenas.ArenaListener;
import mc.alk.arena.objects.events.ArenaEventHandler;
import mc.alk.arena.objects.events.EventPriority;
import mc.alk.arena.objects.teams.ArenaTeam;
import mc.alk.arena.objects.victoryconditions.VictoryCondition;
import mc.alk.arena.serializers.ArenaSerializer;
import mc.alk.arena.serializers.Persist;
import mc.alk.arena.util.TeamUtil;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;

public class TacsArena extends Arena
{
	public static int capturesToWin = 100;
	public static final boolean DEBUG = false;
	private HashSet<String> activePlayers = new HashSet();
	private static final long FLAG_RESPAWN_TIMER = 300L;
	private static final long TIME_BETWEN_CAPTURES = 2000L;
	TeamController teamc = BattleArena.getTeamController();

	@Persist
	final HashMap<Integer, Location> flagSpawns = new HashMap();
	ScoreLimit scores;
	final Map<Integer, Flag> flags = new ConcurrentHashMap();

	final Map<ArenaTeam, Flag> teamFlags = new ConcurrentHashMap();

	Map<String, Integer> playerFlagMapping = new HashMap();

	Map<Integer, Flag> teamFlagMapping = new HashMap();

	int runcount = 0;
	Integer timerid;
	Integer compassRespawnId;
	Integer flagCheckId;
	Map<Flag, Integer> respawnTimers = new HashMap();

	final Map<ArenaTeam, Long> lastCapture = new ConcurrentHashMap();

	final Set<Material> flagMaterials = new HashSet();
	Random rand = new Random();
	MatchMessageHandler mmh;
	public static TacsArena arena;
	int counter = 0;
	private HashMap<String, ChatColor> colorMapping;

	public void onOpen()
	{
		Tacs.getSelf().getLogger().info("Open");
		this.mmh = getMatch().getMessageHandler();
		resetVars();
		getMatch().addVictoryCondition(this.scores);
		arena = this;
	}

	private void resetVars()
	{
		Tacs.getSelf().getLogger().info("Reset");
		VictoryCondition vc = getMatch().getVictoryCondition(ScoreLimit.class);
		this.scores = ((ScoreLimit) (vc != null ? vc : new ScoreLimit(getMatch())));
		this.scores.setScore(capturesToWin);
		this.scores.setMessageHandler(this.mmh);
		this.flags.clear();
		this.teamFlags.clear();
		cancelTimers();
		this.respawnTimers.clear();
		this.lastCapture.clear();
		this.flagMaterials.clear();
	}

	public void onStart()
	{
		Tacs.getSelf().getLogger().info("start");
		List teams = getTeams();

		int i = 0;

		for (Location l : this.flagSpawns.values())
		{
			l = l.clone();
			if (i >= teams.size())
				break;
			ArenaTeam t = (ArenaTeam) teams.get(i);

			ItemStack is = TeamUtil.getTeamHead(i);
			Flag f = new Flag(t, is, l);
			this.teamFlags.put(t, f);

			this.flagMaterials.add(is.getType());

			spawnFlag(f);

			i++;
		}

		this.scores.setFlags(this.teamFlags);

		this.flagCheckId = Integer.valueOf(Bukkit.getScheduler().scheduleSyncRepeatingTask(Tacs.getSelf(), new Runnable()
		{
			public void run()
			{
				for (Flag flag : TacsArena.this.flags.values())
					if ((flag.isHome()) && (!flag.getEntity().isValid()))
						TacsArena.this.spawnFlag(flag);
			}
		}, 0L, 100L));

		this.compassRespawnId = Integer.valueOf(Bukkit.getScheduler().scheduleSyncRepeatingTask(Tacs.getSelf(), new Runnable()
		{
			public void run()
			{
				TacsArena.this.updateCompassLocations();
			}
		}, 0L, 100L));
	}

	private void updateCompassLocations()
	{
		List teams = getTeams();

		for (int i = 0; i < teams.size(); i++)
		{
			int oteam = i == teams.size() - 1 ? 0 : i + 1;
			Flag f = (Flag) this.teamFlags.get(teams.get(oteam));
			if (f == null)
				continue;
			for (ArenaPlayer ap : ((ArenaTeam) teams.get(i)).getLivingPlayers())
			{
				Player p = ap.getPlayer();
				if ((p != null) && (p.isOnline()))
					p.setCompassTarget(f.getCurrentLocation());
			}
		}
	}

	private Item spawnItem(Location l, ItemStack is)
	{
		Item item = l.getBlock().getWorld().dropItem(l, is);
		item.setVelocity(new Vector(0, 0, 0));
		return item;
	}

	public void onFinish()
	{
		cancelTimers();
		removeFlags();
		resetVars();
	}

	@ArenaEventHandler
	public void onPlayerDropItem(PlayerDropItemEvent event)
	{
		if (event.isCancelled())
			return;
		if (!this.flags.containsKey(Integer.valueOf(event.getPlayer().getEntityId())))
			return;
		Item item = event.getItemDrop();
		ItemStack is = item.getItemStack();
		Flag flag = (Flag) this.flags.get(Integer.valueOf(event.getPlayer().getEntityId()));
		if (flag.sameFlag(is))
			playerDroppedFlag(flag, item);
	}

	@ArenaEventHandler
	public void playerKillEvent(ArenaPlayerKillEvent event)
	{
		scores.addScore(event.getTeam(), event.getPlayer());
	}

	protected void addPlayer(ArenaPlayer player, Arena arena, MatchParams mp)
	{
		readPlayerMapping();
		readColorMapping();
		// counter += playerFlagMapping.size();
		// readTeamMapping();
		String[] s = new String[1];
		s[0] = player.getName();

		if (getTeam(player) != null)
		{
			BattleArena.getBAExecutor().join(player, mp, s);
			ArenaTeam t = getTeam(player);
			arena.getMatch().addedToTeam(t, player);
			addFlag(teamFlags.get(t).id, ((Flag) this.teamFlags.get(t)).getHomeLocation());
			spawnFlag((Flag) this.teamFlags.get(t));
		}
		else
		{
			BattleArena.getBAExecutor().join(player, mp, s);
			ArenaTeam t = getTeam(player);
			ItemStack is = null;
			Flag f = null;

			if (playerFlagMapping.containsKey(player.getName()))
			{
				is = TeamUtil.getTeamHead(playerFlagMapping.get(player.getName()));
				f = new Flag(t, is, flagSpawns.get(playerFlagMapping.get(player.getName())));
				t.setTeamChatColor(colorMapping.get(player.getName()));
				t.setScoreboardDisplayName("&" + colorMapping.get(player.getName()).getChar() + player.getName());
				Player tmpPlayer = player.getPlayer();
				tmpPlayer.setDisplayName("&" + colorMapping.get(player.getName()).getChar() + player.getName());
				player.setPlayer(tmpPlayer);
			}
			else
			{
				is = TeamUtil.getTeamHead(this.counter);
				f = new Flag(t, is, ((Location) this.flagSpawns.get(Integer.valueOf(this.counter))).clone());
				playerFlagMapping.put(player.getName(), f.id);
				colorMapping.put(player.getName(), t.getTeamChatColor());
				whireColorMapping();
				writePlayerMapping();
			}
			t.setDisplayName("&" + colorMapping.get(player.getName()).getChar() + player.getName());
			// t.setName("&" + colorMapping.get(player.getName()).getChar() +
			// player.getName());
			this.teamFlags.put(t, f);
			this.flagMaterials.add(is.getType());
			spawnFlag(f);
			this.counter += 1;
			scores.setFlags(teamFlags);
			teamFlagMapping.put(t.getId(), f);
			// this.addArenaListener(new TacsArenaListener());
			// writeTeamMapping();

		}

		this.scores.initScore(player, getTeam(player));
		this.activePlayers.add(player.getName());
	}

	protected void onLeave(ArenaPlayer player, ArenaTeam team)
	{
		Tacs.getSelf().getLogger().info("Leaving: " + player.getName());
		this.activePlayers.remove(player.getName());
		super.onLeave(player, team);
	}

	public static TacsArena getArena()
	{
		return arena;
	}

	// @ArenaEventHandler
	// public void onZPlayerKill(ArenaPlayerKillEvent event) {
	// this.scores.addScore(event.getTeam(), event.getPlayer());
	// }

	@ArenaEventHandler
	public void onPlayerPickupItem(PlayerPickupItemEvent event)
	{
		if (!this.flags.containsKey(Integer.valueOf(event.getItem().getEntityId())))
			return;
		int id = event.getItem().getEntityId();
		Player p = event.getPlayer();
		ArenaTeam t = getTeam(p);
		Flag flag = (Flag) this.flags.get(Integer.valueOf(id));

		Map params = getCaptureParams();
		params.put("{player}", p.getDisplayName());

		ArenaTeam t1 = flag.getTeam();
		if (t1.equals(t))
		{
			event.setCancelled(true);
			if (!flag.isHome())
			{
				this.flags.remove(Integer.valueOf(id));
				event.getItem().remove();
				spawnFlag(flag);
				t.sendMessage(this.mmh.getMessage("CaptureTheFlag.player_returned_flag", params));
			}
		}
		else
		{
			playerPickedUpFlag(p, flag);
			ArenaTeam fteam = flag.getTeam();

			for (ArenaTeam team : getTeams())
				if (team.equals(t))
					team.sendMessage(this.mmh.getMessage("CaptureTheFlag.taken_enemy_flag", params));
				else
					if (team.equals(fteam))
						team.sendMessage(this.mmh.getMessage("CaptureTheFlag.taken_your_flag", params));
		}
	}

	private Map<String, String> getCaptureParams()
	{
		Map params = new HashMap();
		params.put("{prefix}", getMatch().getParams().getPrefix());
		params.put("{maxcaptures}", capturesToWin);
		return params;
	}

	@ArenaEventHandler(needsPlayer = false)
	public void onItemDespawn(ItemDespawnEvent event)
	{
		if (this.flags.containsKey(Integer.valueOf(event.getEntity().getEntityId())))
			event.setCancelled(true);
	}

	@ArenaEventHandler
	public void onPlayerDeath(PlayerDeathEvent event)
	{
		Flag flag = (Flag) this.flags.remove(Integer.valueOf(event.getEntity().getEntityId()));
		if (flag == null)
		{
			return;
		}
		event.getDrops();
		List<ItemStack> items = event.getDrops();
		for (ItemStack is : items)
		{
			if (flag.sameFlag(is))
			{
				int amt = is.getAmount();
				if (amt > 1)
				{
					is.setAmount(amt - 1);
					break;
				}
				is.setType(Material.AIR);
				break;
			}
		}
		Location l = event.getEntity().getLocation();
		Item item = l.getBlock().getWorld().dropItemNaturally(l, flag.is);
		playerDroppedFlag(flag, item);
	}

	@ArenaEventHandler
	public void onPlayerMove(PlayerMoveEvent event)
	{
		if (event.isCancelled())
		{
			return;
		}
		if (((event.getFrom().getBlockX() == event.getTo().getBlockX()) && (event.getFrom().getBlockY() == event.getTo().getBlockY()) && (event.getFrom().getBlockZ() == event.getTo().getBlockZ())) || (!this.flags.containsKey(Integer.valueOf(event.getPlayer().getEntityId()))))
			return;
		if (getMatchState() != MatchState.ONSTART)
			return;
		ArenaTeam t = getTeam(event.getPlayer());
		Flag f = (Flag) this.teamFlags.get(t);

		boolean nearLoc = nearLocation(f.getCurrentLocation(), event.getTo());
		boolean isHome = f.isHome();
		if ((isHome) && (nearLoc))
		{
			Flag capturedFlag = (Flag) this.flags.get(Integer.valueOf(event.getPlayer().getEntityId()));
			Long lastc = (Long) this.lastCapture.get(t);

			if ((lastc != null) && (System.currentTimeMillis() - lastc.longValue() < 2000L))
			{
				return;
			}
			this.lastCapture.put(t, Long.valueOf(System.currentTimeMillis()));

			ArenaPlayer ap = BattleArena.toArenaPlayer(event.getPlayer());
			try
			{
				event.getPlayer().getInventory().remove(f.is);
			}
			catch (Exception localException)
			{
			}
			if (!teamScored(t, ap))
			{
				removeFlag(capturedFlag);
				if (this.activePlayers.contains(capturedFlag.getTeam().getName()))
				{
					spawnFlag(capturedFlag);
				}
				else
				{
					cancelFlagRespawnTimer(capturedFlag);
					Entity ent = capturedFlag.getEntity();
					if ((ent != null) && ((ent instanceof Item)))
						ent.remove();
					if (ent != null)
						this.flags.remove(Integer.valueOf(ent.getEntityId()));
				}
			}
			String score = this.scores.getScoreString();
			Map params = getCaptureParams();
			params.put("{team}", t.getDisplayName());
			params.put("{score}", score);
			this.mmh.sendMessage("CaptureTheFlag.teamscored", params);
		}
	}

	@ArenaEventHandler
	public void onBlockPlace(BlockPlaceEvent event)
	{
		if (!this.flags.containsKey(Integer.valueOf(event.getPlayer().getEntityId())))
		{
			return;
		}

		if (this.flagMaterials.contains(event.getBlock().getType()))
			event.setCancelled(true);
	}

	private void cancelTimers()
	{
		if (this.timerid != null)
		{
			Bukkit.getScheduler().cancelTask(this.timerid.intValue());
			this.timerid = null;
		}
		if (this.compassRespawnId != null)
		{
			Bukkit.getScheduler().cancelTask(this.compassRespawnId.intValue());
			this.compassRespawnId = null;
		}
		if (this.flagCheckId != null)
		{
			Bukkit.getScheduler().cancelTask(this.flagCheckId.intValue());
			this.flagCheckId = null;
		}
	}

	private void removeFlags()
	{
		for (Flag f : this.flags.values())
			removeFlag(f);
	}

	private void removeFlag(Flag flag)
	{
		if ((flag.ent instanceof Player))
			PlayerStoreController.removeItem(BattleArena.toArenaPlayer((Player) flag.ent), flag.is);
		else
			flag.ent.remove();
	}

	public void moveFlag(ArenaPlayer sender, Arena arena, MatchParams mp)
	{
		Flag f = (Flag) this.teamFlags.get(getTeam(sender));

		if ((f.getPlaceTime() != null) && (f.getPlaceTime().getTime() + 86400000L > new Date().getTime()))
		{
			sender.sendMessage("You can only move your flag once a day");
			return;
		}

		f.setHomeLocation(sender.getLocation().add(0, 10, 0).clone());
		this.flagSpawns.put(Integer.valueOf(f.id), sender.getLocation().add(0, 10, 0).clone());
		ArenaSerializer.saveArenas(Tacs.getSelf());
		spawnFlag(f);
	}

	private void playerPickedUpFlag(Player player, Flag flag)
	{
		this.flags.remove(Integer.valueOf(flag.ent.getEntityId()));
		flag.setEntity(player);
		flag.setHome(false);
		this.flags.put(Integer.valueOf(player.getEntityId()), flag);
		cancelFlagRespawnTimer(flag);
	}

	private void playerDroppedFlag(Flag flag, Item item)
	{
		this.flags.remove(Integer.valueOf(flag.ent.getEntityId()));
		flag.setEntity(item);
		this.flags.put(Integer.valueOf(item.getEntityId()), flag);
		startFlagRespawnTimer(flag);
	}

	private void spawnFlag(Flag flag)
	{
		cancelFlagRespawnTimer(flag);
		Entity ent = flag.getEntity();
		if ((ent != null) && ((ent instanceof Item)))
			ent.remove();
		if (ent != null)
			this.flags.remove(Integer.valueOf(ent.getEntityId()));
		Location l = flag.getHomeLocation();
		Item item = spawnItem(l, flag.is);
		flag.setEntity(item);
		flag.setHome(true);
		this.flags.put(Integer.valueOf(item.getEntityId()), flag);
	}

	private void startFlagRespawnTimer(final Flag flag)
	{
		cancelFlagRespawnTimer(flag);
		Integer timerid = Bukkit.getScheduler().scheduleSyncDelayedTask(Tacs.getSelf(), new Runnable()
		{
			@Override
			public void run()
			{
				spawnFlag(flag);
				ArenaTeam team = flag.getTeam();
				Map<String, String> params = getCaptureParams();
				team.sendMessage(mmh.getMessage("CaptureTheFlag.returned_flag", params));
			}
		}, FLAG_RESPAWN_TIMER);
		respawnTimers.put(flag, timerid);
	}

	private void cancelFlagRespawnTimer(Flag flag)
	{
		Integer timerid = (Integer) this.respawnTimers.get(flag);
		if (timerid != null)
			Bukkit.getScheduler().cancelTask(timerid.intValue());
	}

	private synchronized boolean teamScored(ArenaTeam team, ArenaPlayer player)
	{
		return this.scores.addScore(team, player);
	}

	public static boolean nearLocation(Location l1, Location l2)
	{
		return (l1.getWorld().getUID().equals(l2.getWorld().getUID())) && (Math.abs(l1.getX() - l2.getX()) < 2.0D) && (Math.abs(l1.getZ() - l2.getZ()) < 2.0D) && (Math.abs(l1.getBlockY() - l2.getBlockY()) < 3);
	}

	public Map<Integer, Location> getFlagLocations()
	{
		return this.flagSpawns;
	}

	public void addFlag(Integer i, Location location)
	{
		Location l = location.clone();
		l.setX(location.getBlockX() + 0.5D);
		l.setY(location.getBlockY() + 2);
		l.setZ(location.getBlockZ() + 0.5D);
		this.flagSpawns.put(i, l);
	}

	public void clearFlags()
	{
		this.flagSpawns.clear();
	}

	public boolean valid()
	{
		return super.valid();
	}

	public void removePlayer(Player player)
	{
		// Set<ArenaPlayer> players = this.getAlivePlayers();
		// ArenaPlayer currPlayer = null;
		// for(ArenaPlayer p : players)
		// {
		// if(p.getName() == player.getName())
		// {
		// currPlayer = p;
		// }
		// }
		this.activePlayers.remove(player.getName());
		// BattleArena.getBAExecutor().leave(currPlayer,
		// this.getMatch().getParams(), true);
	}

	String fileLoc1 = System.getProperty("user.dir") + "\\plugins\\ArenaTactics\\mapping1.txt";
	String fileLoc2 = System.getProperty("user.dir") + "\\plugins\\ArenaTactics\\mapping2.txt";
	String fileLoc3 = System.getProperty("user.dir") + "\\plugins\\ArenaTactics\\mapping3.txt";

	private void writeTeamMapping()
	{
		try
		{
			File f = new File(this.fileLoc1);
			if (f.exists())
			{
				f.delete();
			}
			else
			{
				f.createNewFile();
			}

			FileOutputStream fileOut = new FileOutputStream(f);
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(this.teamFlagMapping);
			out.close();
			fileOut.close();
		}
		catch (IOException io)
		{
			Tacs.getSelf().getLogger().warning(io.getMessage());
		}
	}

	public void readTeamMapping()
	{
		try
		{
			File f = new File(this.fileLoc1);
			if ((!f.exists()) || (f.length() == 0L))
			{
				f.createNewFile();
				this.teamFlagMapping = new HashMap();
				return;
			}

			FileInputStream fileIn = new FileInputStream(f);
			ObjectInputStream in = new ObjectInputStream(fileIn);
			this.teamFlagMapping = ((HashMap) in.readObject());
			in.close();
			fileIn.close();
		}
		catch (IOException i)
		{
			Tacs.getSelf().getLogger().warning(i.getMessage());
		}
		catch (ClassNotFoundException e)
		{
			Tacs.getSelf().getLogger().warning(e.getMessage());
		}
	}

	private void whireColorMapping()
	{
		try
		{
			File f = new File(this.fileLoc3);
			if (f.exists())
			{
				f.delete();
			}
			else
			{
				f.createNewFile();
			}

			FileOutputStream fileOut = new FileOutputStream(f);
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(this.colorMapping);
			out.close();
			fileOut.close();
		}
		catch (IOException io)
		{
			Tacs.getSelf().getLogger().warning(io.getMessage());
		}
	}

	private void writePlayerMapping()
	{
		try
		{
			File f = new File(this.fileLoc2);
			if (f.exists())
			{
				f.delete();
			}
			else
			{
				f.createNewFile();
			}

			FileOutputStream fileOut = new FileOutputStream(f);
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(this.playerFlagMapping);
			out.close();
			fileOut.close();
		}
		catch (IOException io)
		{
			Tacs.getSelf().getLogger().warning(io.getMessage());
		}
	}

	public void readColorMapping()
	{

		try
		{
			File f = new File(this.fileLoc3);
			if ((!f.exists()) || (f.length() == 0L))
			{
				f.createNewFile();
				this.colorMapping = new HashMap();
				return;
			}

			FileInputStream fileIn = new FileInputStream(f);
			ObjectInputStream in = new ObjectInputStream(fileIn);
			this.colorMapping = ((HashMap) in.readObject());
			in.close();
			fileIn.close();
		}
		catch (IOException i)
		{
			Tacs.getSelf().getLogger().warning(i.getMessage());
		}
		catch (ClassNotFoundException e)
		{
			Tacs.getSelf().getLogger().warning(e.getMessage());
		}
	}

	public void readPlayerMapping()
	{
		try
		{
			File f = new File(this.fileLoc2);
			if ((!f.exists()) || (f.length() == 0L))
			{
				f.createNewFile();
				this.playerFlagMapping = new HashMap();
				return;
			}

			FileInputStream fileIn = new FileInputStream(f);
			ObjectInputStream in = new ObjectInputStream(fileIn);
			this.playerFlagMapping = ((HashMap) in.readObject());
			in.close();
			fileIn.close();
		}
		catch (IOException i)
		{
			Tacs.getSelf().getLogger().warning(i.getMessage());
		}
		catch (ClassNotFoundException e)
		{
			Tacs.getSelf().getLogger().warning(e.getMessage());
		}
	}
}