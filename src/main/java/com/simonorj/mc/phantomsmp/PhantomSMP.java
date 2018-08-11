package com.simonorj.mc.phantomsmp;

import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class PhantomSMP extends JavaPlugin {
    private Map<Player, LinkedHashSet<Phantom>> playerPhantomMap = null;
    private Map<Phantom, Player> phantomPlayerMap = null;
    private static final int TIME_SINCE_REST_PHANTOM_SPAWN = 72000;

    @Override
    public void onEnable() {
        this.playerPhantomMap = new HashMap<>();
        this.phantomPlayerMap = new HashMap<>();

        // Initiate map
        for (World w : getServer().getWorlds()) {
            if (w.getEnvironment() != World.Environment.NORMAL)
                continue;

            for (Entity e : w.getLivingEntities())
                if (e instanceof Phantom)
                    addPhantom((Phantom) e);
        }

        getServer().getPluginManager().registerEvents(new PhantomListener(), this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length != 0 && sender instanceof Player) {
            int val;
            try {
                val = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                return false;
            }

            Player p = (Player) sender;

            p.setStatistic(Statistic.TIME_SINCE_REST, val);
            p.sendMessage("Set TSR to " + val);
            return true;
        }

        return false;
    }

    public void onDisable() {
        playerPhantomMap = null;
        phantomPlayerMap = null;
    }

    private void addPhantom(Phantom phantom) {
        addPhantom(phantom, null);
    }

    private void addPhantom(Phantom phantom, Cancellable e) {
        addPhantom(phantom, null, e);
    }

    private void addPhantom(Phantom phantom, Player newTarget, Cancellable e) {
        if (newTarget == null && !(phantom.getTarget() instanceof Player)) {
            return;
        }

        Player p = newTarget != null ? newTarget : (Player) phantom.getTarget();

        // Player rested before
        if (!phantomSpawnAllowed(p)) {
            getLogger().info("Removed illegally targeting phantom");
            Thread.dumpStack();
            if (e != null)
                e.setCancelled(true);
            // TODO: Quesiton: Also remove the phantom?
            //else
            phantom.remove();
        }

        // Phantom spawn is legal
        playerPhantomMap.computeIfAbsent(p, k -> new LinkedHashSet<>()).add(phantom);
        phantomPlayerMap.put(phantom, p);
    }

    private void removePhantom(Phantom phantom) {
        Player p = phantomPlayerMap.remove(phantom);
        if (p == null)
            return;

        playerPhantomMap.get(p).remove(phantom);

        phantom.remove();
    }

    private void removePlayerPhantom(Player p) {
        Iterator<Phantom> i = playerPhantomMap.get(p).iterator();
        while(i.hasNext()) {
            Phantom phantom = i.next();
            if (phantom.getTarget() == p) {
                phantomPlayerMap.remove(phantom);
                phantom.remove();
            }
            i.remove();
        }
    }

    private boolean phantomSpawnAllowed(Player p) {
        return p.getStatistic(Statistic.TIME_SINCE_REST) > TIME_SINCE_REST_PHANTOM_SPAWN;
    }

    public class PhantomListener implements Listener {
        // Initiate when player joins
        @EventHandler(priority = EventPriority.MONITOR)
        public void playerJoin(PlayerJoinEvent e) {
            playerPhantomMap.put(e.getPlayer(), new LinkedHashSet<>());
        }

        // Reset when player leaves
        @EventHandler
        public void playerLeave(PlayerQuitEvent e) {
            Player p = e.getPlayer();
            Iterator<Map.Entry<Phantom, Player>> i = phantomPlayerMap.entrySet().iterator();
            while (i.hasNext()) {
                Map.Entry<Phantom, Player> entry = i.next();
                if (entry.getValue() == p) {
                    i.remove();
                    entry.getKey().setTarget(null);
                }
            }
        }

        // Remove phantoms when player sleeps
        @EventHandler
        public void playerUseBed(PlayerBedEnterEvent e) {
            if (e.isCancelled())
                return;

            removePlayerPhantom(e.getPlayer());
        }

        @EventHandler
        public void playerDied(PlayerDeathEvent e) {
            removePlayerPhantom(e.getEntity());
        }

        // Check phantom when they spawn wrongly
        @EventHandler
        public void phantomSpawn(CreatureSpawnEvent e) {
            if (e.isCancelled() || !(e.getEntity() instanceof Phantom)) {
                return;
            }

            addPhantom((Phantom) e.getEntity(), e);
        }

        // Remove phantom that targets player who slept
        @EventHandler
        public void phantomTarget(EntityTargetLivingEntityEvent e) {
            if (e.isCancelled() || !(e.getEntity() instanceof Phantom && e.getTarget() instanceof Player)) {
                return;
            }

            addPhantom((Phantom) e.getEntity(), (Player) e.getTarget(), e);
        }

        // Check phantom in loaded chunks
        @EventHandler
        public void phantomInLoadedChunk(ChunkLoadEvent e) {
            if (e.getWorld().getEnvironment() != World.Environment.NORMAL)
                return;

            for (Entity ent : e.getChunk().getEntities())
                if (ent instanceof Phantom)
                    addPhantom((Phantom) ent);
        }

        // Check phantom on death
        @EventHandler
        public void phantomDied(EntityDeathEvent e) {
            if (!(e.getEntity() instanceof Phantom))
                return;

            removePhantom((Phantom) e.getEntity());
        }
    }
}