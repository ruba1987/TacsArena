package com.gmail.russelljbaker.arena.tacs;

import java.util.Date;
import mc.alk.arena.objects.teams.ArenaTeam;
import mc.alk.arena.util.InventoryUtil;
import mc.alk.arena.util.SerializerUtil;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

public class Flag
{
  static int count = 0;
  final int id = count++;
  Entity ent;
  private Date placeTime = null;
  boolean home;
  final ArenaTeam team;
  final ItemStack is;
  Location homeLocation;

  public Flag(ArenaTeam team, ItemStack is, Location homeLocation)
  {
    this.team = team;
    this.home = true;
    this.is = is;
    this.homeLocation = homeLocation;
  }
  public void setEntity(Entity entity) {
    this.ent = entity; } 
  public Location getCurrentLocation() { return this.ent.getLocation(); } 
  public Location getHomeLocation() { return this.homeLocation; }

  public boolean sameFlag(ItemStack is2) {
    return (this.is.getType() == is2.getType()) && (this.is.getDurability() == is2.getDurability());
  }

  public boolean equals(Object other)
  {
    if (this == other) return true;
    if (!(other instanceof Flag)) return false;
    return hashCode() == ((Flag)other).hashCode();
  }

  public int hashCode() {
    return this.id;
  }
  public Entity getEntity() {
    return this.ent;
  }

  public ArenaTeam getTeam() {
    return this.team;
  }

  public boolean isHome() {
    return this.home;
  }

  public void setHome(boolean home) {
    this.home = home;
  }

  public void setHomeLocation(Location l) {
    this.homeLocation = l.clone();
    this.placeTime = new Date();
  }

  public Date getPlaceTime()
  {
    return this.placeTime;
  }

  public String toString()
  {
    return String.format("[Flag %d: ent=%s, home=%s, team=%d, is=%s, homeloc=%s]", new Object[] { 
      Integer.valueOf(this.id), this.ent == null ? "null" : this.ent.getType(), Boolean.valueOf(this.home), 
      this.team == null ? "null" : Integer.valueOf(this.team.getId()), 
      this.is == null ? "null" : InventoryUtil.getItemString(this.is), 
      this.homeLocation == null ? "null" : SerializerUtil.getLocString(this.homeLocation) });
  }
}