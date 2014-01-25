package com.gmail.russell.j.baker.arena.tacs;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;
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

import mc.alk.arena.BattleArena;
import mc.alk.arena.controllers.EventController;
import mc.alk.arena.controllers.PlayerStoreController;
import mc.alk.arena.controllers.TeamController;
import mc.alk.arena.controllers.messaging.MatchMessageHandler;
import mc.alk.arena.objects.ArenaPlayer;
import mc.alk.arena.objects.MatchParams;
import mc.alk.arena.objects.MatchState;
import mc.alk.arena.objects.arenas.Arena;
import mc.alk.arena.objects.events.ArenaEventHandler;
import mc.alk.arena.objects.teams.ArenaTeam;
import mc.alk.arena.objects.victoryconditions.VictoryCondition;
import mc.alk.arena.serializers.Persist;
import mc.alk.arena.util.TeamUtil;

public class TacsArena extends Arena {
	//public static int scoreToWin = 20;
	public static int capturesToWin = 5;
	
	public static final boolean DEBUG = false;
	private HashSet<String> activePlayers = new HashSet<String>();
	private Map<String, Flag> cachedFlags = new HashMap<String, Flag>();
	private static final long FLAG_RESPAWN_TIMER = 20*15L;
	private static final long TIME_BETWEN_CAPTURES = 2000;
	TeamController teamc = BattleArena.getTeamController();

	/**
	 * Save these flag spawns with the rest of the arena information
	 */
	@Persist
	final HashMap<Integer,Location> flagSpawns = new HashMap<Integer,Location>();

	/// The following variables should be reinitialized and set up every match
	ScoreLimit scores;

	final Map<Integer, Flag> flags = new ConcurrentHashMap<Integer, Flag>();

	final Map<ArenaTeam, Flag> teamFlags = new ConcurrentHashMap<ArenaTeam, Flag>();

	int runcount = 0;

	Integer timerid, compassRespawnId, flagCheckId;

	Map<Flag, Integer> respawnTimers = new HashMap<Flag,Integer>();

	final Map<ArenaTeam, Long> lastCapture = new ConcurrentHashMap<ArenaTeam, Long>();

	final Set<Material> flagMaterials = new HashSet<Material>();
	EventController ec;
	Random rand = new Random();
	MatchMessageHandler mmh;

	@Override
	public void onOpen(){
		mmh = getMatch().getMessageHandler();
		resetVars();
		getMatch().addVictoryCondition(scores);
        this.ec = BattleArena.getEventController();		
		
	}

	private void resetVars(){
		VictoryCondition vc = getMatch().getVictoryCondition(ScoreLimit.class);
		scores = (ScoreLimit) (vc != null ? vc : new ScoreLimit(getMatch()));
		scores.setScore(capturesToWin);
		scores.setMessageHandler(mmh);
		flags.clear();
		teamFlags.clear();
		cancelTimers();
		respawnTimers.clear();
		lastCapture.clear();
		flagMaterials.clear();
	}

	@Override
	public void onStart(){
		List<ArenaTeam> teams = getTeams();
//		if (flagSpawns.size() < teams.size()){
//			//err("Cancelling CTF as there " + teams.size()+" teams but only " + flagSpawns.size() +" flags");
//			getMatch().cancelMatch();
//			return;
//		}

		/// Set all scores to 0
		/// Set up flag locations
		int i =0;
		
		for (Location l: flagSpawns.values()){
			l = l.clone();
			if(i >= teams.size())
				break;
			ArenaTeam t = teams.get(i);
			/// Create our flag
			ItemStack is = TeamUtil.getTeamHead(i);
			Flag f = new Flag(t,is,l);
			teamFlags.put(t, f);

			/// add to our materials
			flagMaterials.add(is.getType());

			spawnFlag(f);

			i++;
			if (DEBUG) System.out.println("Team t = " + t);
		}
		scores.setFlags(teamFlags);
		/// Schedule flame effects
//		timerid = Bukkit.getScheduler().scheduleSyncRepeatingTask(Tacs.getSelf(), new Runnable(){
//			@Override
//			public void run() {
//				boolean extraeffects = (runcount++ % 2 == 0);
//				for (Flag flag: flags.values()){
//					Location l = flag.getCurrentLocation();
//					l.getWorld().playEffect(l, Effect.MOBSPAWNER_FLAMES, 0);
//					if (extraeffects){
//						l = l.clone();
//						l.setX(l.getX() + rand.nextInt(4)-2);
//						l.setZ(l.getZ() + rand.nextInt(4)-2);
//						l.setY(l.getY() + rand.nextInt(2)-1);
//						l.getWorld().playEffect(l, Effect.MOBSPAWNER_FLAMES, 0);
//					}
//				}
//			}
//		}, 20L,20L);

		/// Schedule flag checks
		flagCheckId = Bukkit.getScheduler().scheduleSyncRepeatingTask(Tacs.getSelf(), new Runnable(){
			@Override
			public void run() {
				for (Flag flag: flags.values()){
					if (flag.isHome() && !flag.getEntity().isValid()){
						spawnFlag(flag);
					}
				}
			}
		}, 0L, 5*20L);

		/// Schedule compass Updates
		compassRespawnId = Bukkit.getScheduler().scheduleSyncRepeatingTask(Tacs.getSelf(), new Runnable(){
			@Override
			public void run() {updateCompassLocations();}
		}, 0L, 5*20L);
	}

	private void updateCompassLocations() {
		List<ArenaTeam> teams = getTeams();
		Flag f;
		for (int i=0;i<teams.size();i++){
			int oteam = i == teams.size()-1 ? 0 : i+1;
			f = teamFlags.get(teams.get(oteam));
			if (f == null)
				continue;
			for (ArenaPlayer ap: teams.get(i).getLivingPlayers()){
				Player p = ap.getPlayer();
				if (p != null && p.isOnline()){
					p.setCompassTarget(f.getCurrentLocation());
				}
			}
		}
	}

	private Item spawnItem(Location l, ItemStack is) {
		Item item = l.getBlock().getWorld().dropItem(l,is);
		item.setVelocity(new Vector(0,0,0));
		return item;
	}
	
	
	@Override
	public void onFinish(){
		cancelTimers();
		removeFlags();
		resetVars();
	}

	@ArenaEventHandler
	public void onPlayerDropItem(PlayerDropItemEvent event){
		if (event.isCancelled())
			return ;
		if (!flags.containsKey(event.getPlayer().getEntityId())){
			return;}
		Item item = event.getItemDrop();
		ItemStack is = item.getItemStack();
		Flag flag = flags.get(event.getPlayer().getEntityId());
		if (flag.sameFlag(is)){ /// Player is dropping a flag
			playerDroppedFlag(flag, item);}
	}
	
	Map<String, ArenaTeam> playerMappings = new ConcurrentHashMap<String, ArenaTeam>();
	
	//@Override
	protected void addPlayer(ArenaPlayer player, Arena arena, MatchParams mp) 
	{
			//TODO: add code to support respawning the players flag when they come back into the game
			if(playerMappings.containsKey(player.getDisplayName()))
			{
				arena.getMatch().addedToTeam(playerMappings.get(player.getDisplayName()), player);
				Tacs.getSelf().getLogger().info("Team: " + cachedFlags.get(player.getDisplayName()));
				addFlag(cachedFlags.get(playerMappings.get(player.getDisplayName()).getDisplayName()).id, cachedFlags.get(playerMappings.get(player.getDisplayName()).getDisplayName()).getHomeLocation());
				spawnFlag(cachedFlags.get(playerMappings.get(player.getDisplayName()).getDisplayName()));
				cachedFlags.remove(playerMappings.get(player.getDisplayName()).getDisplayName());
			}
			else
			{
				//addFlag(flags.size()+1, new Location(player.getLocation().getWorld(), 201.05394, 123.000, 318.06244));
				BattleArena.getBAExecutor().join(player, mp, new String[1]);
			}
			
			activePlayers.add(player.getDisplayName());
			
			
	};
	
	@Override
	protected void onLeave(ArenaPlayer player, ArenaTeam team){
		Tacs.getSelf().getLogger().info("Leaving: " + player.getDisplayName());
		playerMappings.put(player.getDisplayName(), team);
		activePlayers.remove(player.getDisplayName());
		super.onLeave(player, team);
	}
	
	

	@ArenaEventHandler
	public void onPlayerPickupItem(PlayerPickupItemEvent event){
		if (!flags.containsKey(event.getItem().getEntityId())){
			return;}
		final int id = event.getItem().getEntityId();
		final Player p = event.getPlayer();
		final ArenaTeam t = getTeam(p);
		final Flag flag = flags.get(id);

		Map<String,String> params = getCaptureParams();
		params.put("{player}",p.getDisplayName());
		/// for some reason if I do flags.remove here... event.setCancelled(true) doesnt work!!!! oO
		/// If anyone can explain this... I would be ecstatic, seriously.
		if (flag.getTeam().equals(t)){
			event.setCancelled(true);
			if (!flag.isHome()){  /// Return the flag back to its home location
				flags.remove(id);
				event.getItem().remove();
				spawnFlag(flag);
				t.sendMessage(mmh.getMessage("CaptureTheFlag.player_returned_flag", params));
			}
		} else {
			/// Give the enemy the flag
			playerPickedUpFlag(p,flag);
			ArenaTeam fteam = flag.getTeam();

			for (ArenaTeam team : getTeams()){
				if (team.equals(t)){
					team.sendMessage(mmh.getMessage("CaptureTheFlag.taken_enemy_flag", params));
				} else if (team.equals(fteam)){
					team.sendMessage(mmh.getMessage("CaptureTheFlag.taken_your_flag", params));
				}
			}
		}
	}

	private Map<String, String> getCaptureParams() {
		Map<String,String> params = new HashMap<String,String>();
		params.put("{prefix}", getMatch().getParams().getPrefix());
		params.put("{maxcaptures}", capturesToWin+"");
		return params;
	}

	@ArenaEventHandler(needsPlayer=false)
	public void onItemDespawn(ItemDespawnEvent event){
		if (flags.containsKey(event.getEntity().getEntityId())){
			event.setCancelled(true);
		}
	}

	@ArenaEventHandler
	public void onPlayerDeath(PlayerDeathEvent event){
		Flag flag = flags.remove(event.getEntity().getEntityId());
		if (flag == null)
			return;
		/// we have a flag holder, drop the flag
		event.getDrops();
		List<ItemStack> items = event.getDrops();
		for (ItemStack is : items){
			if (flag.sameFlag(is)){
				final int amt = is.getAmount();
				if (amt > 1)
					is.setAmount(amt-1);
				else
					is.setType(Material.AIR);
				break;
			}
		}
		Location l = event.getEntity().getLocation();
		Item item = l.getBlock().getWorld().dropItemNaturally(l,flag.is);
		playerDroppedFlag(flag, item);
	}

	@ArenaEventHandler
	public void onPlayerMove(PlayerMoveEvent event){
		if (event.isCancelled())
			return;
		//TODO stop flags from respawning if a player is dead after the is captured
		/// Check to see if they moved a block, or if they are holding a flag
		if (!(event.getFrom().getBlockX() != event.getTo().getBlockX()
				|| event.getFrom().getBlockY() != event.getTo().getBlockY()
				|| event.getFrom().getBlockZ() != event.getTo().getBlockZ())
				|| !flags.containsKey(event.getPlayer().getEntityId())){
			return;}
		if (this.getMatchState() != MatchState.ONSTART)
			return;
		ArenaTeam t = getTeam(event.getPlayer());
		Flag f = teamFlags.get(t);
		//Tacs.getSelf().getLogger().info("Event LOC:" + event.getTo());
		//Tacs.getSelf().getLogger().info("Curr LOC:" + f.getCurrentLocation());
		if (f.isHome() && nearLocation(f.getCurrentLocation(),event.getTo())){
			Flag capturedFlag = flags.get(event.getPlayer().getEntityId());
			Long lastc = lastCapture.get(t);
			/// For some servers multiple teamScored methods were being called, possibly due to a back log of onPlayerMove
			/// Events that went off nearly simultaneously before flags could be reset, so now make teamScored synchronized,
			/// and check the last capture time
			if (lastc != null && System.currentTimeMillis() - lastc < TIME_BETWEN_CAPTURES){
				return;
			} else {
				lastCapture.put(t, System.currentTimeMillis());
			}
			ArenaPlayer ap = BattleArena.toArenaPlayer(event.getPlayer());
			/// for some reason sometimes its not cleared in removeFlags
			/// so do it explicitly now
			try{event.getPlayer().getInventory().remove(f.is);}catch(Exception e){}

			if (!teamScored(t,ap)){
				removeFlag(capturedFlag);
				if(activePlayers.contains(capturedFlag.getTeam().getDisplayName()))
					spawnFlag(capturedFlag);
				else{
					cachedFlags.put(capturedFlag.getTeam().getDisplayName(), capturedFlag);
					//removeFlag(capturedFlag);
				}
			}
			String score = scores.getScoreString();
			Map<String,String> params = getCaptureParams();
			params.put("{team}", t.getDisplayName());
			params.put("{score}", score);
			mmh.sendMessage("CaptureTheFlag.teamscored",params);

		}
	}

	@ArenaEventHandler
	public void onBlockPlace(BlockPlaceEvent event){
		if (!flags.containsKey(event.getPlayer().getEntityId()))
			return;
		/// We don't want people "placing" flags.  This could be extended to be exactly the type and short value
		/// but until its needed. this is fine
		if (flagMaterials.contains(event.getBlock().getType())){
			event.setCancelled(true);}
	}

	private void cancelTimers(){
		if (timerid != null){
			Bukkit.getScheduler().cancelTask(timerid);
			timerid = null;
		}
		if (compassRespawnId != null){
			Bukkit.getScheduler().cancelTask(compassRespawnId);
			compassRespawnId = null;
		}
		if (flagCheckId != null){
			Bukkit.getScheduler().cancelTask(flagCheckId);
			flagCheckId = null;
		}
	}

	private void removeFlags() {
		for (Flag f: flags.values()){
			removeFlag(f);
		}
	}

	private void removeFlag(Flag flag){
		if (flag.ent instanceof Player){
			PlayerStoreController.removeItem(BattleArena.toArenaPlayer((Player) flag.ent), flag.is);
		} else {
			flag.ent.remove();
		}
	}

	private void playerPickedUpFlag(Player player, Flag flag) {
		flags.remove(flag.ent.getEntityId());
		flag.setEntity(player);
		flag.setHome(false);
		flags.put(player.getEntityId(), flag);
		cancelFlagRespawnTimer(flag);
	}

	private void playerDroppedFlag(Flag flag, Item item) {
		flags.remove(flag.ent.getEntityId());
		flag.setEntity(item);
		flags.put(item.getEntityId(), flag);
		startFlagRespawnTimer(flag);
	}

	private void spawnFlag(Flag flag){
		cancelFlagRespawnTimer(flag);
		Entity ent = flag.getEntity();
		if (ent != null && ent instanceof Item){
			ent.remove();}
		if (ent != null)
			flags.remove(ent.getEntityId());
		Location l = flag.getHomeLocation();
		Item item = spawnItem(l,flag.is);
		flag.setEntity(item);
		flag.setHome(true);
		flags.put(item.getEntityId(), flag);
	}

	private void startFlagRespawnTimer(final Flag flag) {
		cancelFlagRespawnTimer(flag);
		Integer timerid = Bukkit.getScheduler().scheduleSyncDelayedTask(Tacs.getSelf(), new Runnable(){
			@Override
			public void run() {
				spawnFlag(flag);
				ArenaTeam team = flag.getTeam();
				Map<String,String> params = getCaptureParams();
				team.sendMessage(mmh.getMessage("CaptureTheFlag.returned_flag",params));
			}
		}, FLAG_RESPAWN_TIMER);
		respawnTimers.put(flag, timerid);
	}

	private void cancelFlagRespawnTimer(Flag flag){
		Integer timerid = respawnTimers.get(flag);
		if (timerid != null)
			Bukkit.getScheduler().cancelTask(timerid);
	}

	private synchronized boolean teamScored(ArenaTeam team, ArenaPlayer player) {		
		return scores.addScore(team,player);
	}

	public static boolean nearLocation(final Location l1, final Location l2){
		return l1.getWorld().getUID().equals(l2.getWorld().getUID()) && Math.abs(l1.getX() - l2.getX()) < 2
				&& Math.abs(l1.getZ() - l2.getZ()) < 2 && Math.abs(l1.getBlockY() - l2.getBlockY()) < 3;
	}

	public Map<Integer, Location> getFlagLocations() {
		return flagSpawns;
	}

	public void addFlag(Integer i, Location location) {
		Location l = location.clone();
		l.setX(location.getBlockX()+0.5);
		l.setY(location.getBlockY()+2);
		l.setZ(location.getBlockZ()+0.5);
		flagSpawns.put(i, l);
	}

	public void clearFlags() {
		flagSpawns.clear();
	}


	@Override
	public boolean valid(){
		return super.valid();
	}

//	@Override
//	public List<String> getInvalidReasons(){
//		List<String> reasons = new ArrayList<String>();
//		if (flagSpawns == null || flagSpawns.size() < 2){
//			reasons.add("You need to add at least 2 flags!");}
//		reasons.addAll(super.getInvalidReasons());
//		return reasons;
//	}

}
