package com.github.devotedmc.hiddenore.tracking;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;

import com.github.devotedmc.hiddenore.Config;
import com.github.devotedmc.hiddenore.HiddenOre;

public class BreakTracking {
	private static final int GEN = 1;
	private static final int MAP = 0;
	Map<UUID, Map<Long, short[]>> track;
	Map<UUID, Map<Long, long[][][]>> map;
	
	// Now for one small data structures that will let us keep track of most-recent-breaks to directly
	// prevent gaming.
	
	private static final int RECENT_MAX = 512;
	private int recentPtr;
	private Location[] recent;

	public BreakTracking() {
		track = new HashMap<UUID, Map<Long, short[]>>();
		map = new HashMap<UUID, Map<Long, long[][][]>>();
		recent = new Location[RECENT_MAX];
		recentPtr = 0;
	}

	public void load() {
		long s = System.currentTimeMillis();
		HiddenOre.getPlugin().getLogger().info("Starting Break Tracking load");
		File tf = Config.getTrackFile();
		if (!tf.exists()) {
			HiddenOre.getPlugin().getLogger().info("No save exists to load");
		} else {
			try {
				DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(tf)));

				String uuid = null;
				boolean active = true;
				while (active) {
					try {
						uuid = dis.readUTF();
						HiddenOre.getPlugin().getLogger().info("Loading tracking data for world " + uuid);
					} catch (EOFException done) {
						active = false;
						break;
					}
					UUID uid = UUID.fromString(uuid);
					Map<Long, short[]> world = new HashMap<Long, short[]>();
					track.put(uid, world);

					long ccnt = 0l;

					while (dis.readBoolean()) {
						Long chunk = dis.readLong();
						short[] layers = new short[256];
						for (int i = 0; i < layers.length; i++) {
							layers[i] = dis.readShort();
						}
						if (Config.isDebug) {
							HiddenOre.getPlugin().getLogger().info("Loaded layers for chunk " + chunk);
						}
						ccnt++;
						world.put(chunk, layers);
					}

					HiddenOre.getPlugin().getLogger().info("Loaded " + ccnt + " chunks");
				}

				dis.close();
			} catch (IOException ioe) {
				HiddenOre.getPlugin().getLogger().log(Level.SEVERE, "Failed to load break tracking.", ioe);
			}
		}
		s = System.currentTimeMillis() - s;
		HiddenOre.getPlugin().getLogger().info("Took " + s + "ms to load Break Tracking");
		
		s = System.currentTimeMillis();
		HiddenOre.getPlugin().getLogger().info("Starting Break Map load");
		tf = Config.getMapFile();
		if (!tf.exists()) {
			HiddenOre.getPlugin().getLogger().info("No map save exists to load");
		} else {
			try {
				DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(tf)));

				String uuid = null;
				boolean active = true;
				while (active) {
					try {
						uuid = dis.readUTF();
						HiddenOre.getPlugin().getLogger().info("Loading mapping data for world " + uuid);
					} catch (EOFException done) {
						active = false;
						break;
					}
					UUID uid = UUID.fromString(uuid);
					Map<Long, long[][][]> world = new HashMap<Long, long[][][]>();
					map.put(uid, world);

					long ccnt = 0l;

					while (dis.readBoolean()) {
						Long chunk = dis.readLong();
						long[][][] layers = new long[2][256][4];
						for (int i = 0; i < layers[MAP].length; i++) {
							for (int j = 0; j < 4; j++) {
								layers[MAP][i][j] = dis.readLong(); // "map"
							}
						}
						for (int i = 0; i < layers[GEN].length; i++) {
							for (int j = 0; j < 4; j++) {
								layers[GEN][i][j] = dis.readLong(); // "gen"
							}
						}

						if (Config.isDebug) {
							HiddenOre.getPlugin().getLogger().info("Loaded layers for chunk " + chunk);
						}
						ccnt++;
						world.put(chunk, layers);
					}

					HiddenOre.getPlugin().getLogger().info("Loaded " + ccnt + " chunks");
				}

				dis.close();
			} catch (IOException ioe) {
				HiddenOre.getPlugin().getLogger().log(Level.SEVERE, "Failed to load break map.", ioe);
			}
		}
		s = System.currentTimeMillis() - s;
		HiddenOre.getPlugin().getLogger().info("Took " + s + "ms to load Break Map");
	}

	public void liveSave() {
		Bukkit.getScheduler().runTaskLaterAsynchronously(HiddenOre.getPlugin(), new Runnable() {
			public void run() {
				save();
			}
		}, 0);

		Bukkit.getScheduler().runTaskLaterAsynchronously(HiddenOre.getPlugin(), new Runnable() {
			public void run() {
				saveMap();
			}
		}, 0);

	}

	public void save() {
		long s = System.currentTimeMillis();
		HiddenOre.getPlugin().getLogger().info("Starting Break Tracking save");
		File tf = Config.getTrackFile();
		if (tf.exists()) {
			File tfb = new File(tf.getAbsoluteFile() + ".backup");
			if (tfb.exists()) {
				if (!tfb.delete()) {
					HiddenOre.getPlugin().getLogger().info("Couldn't remove old backup file - " + tfb);
				} else {
					tf.renameTo(tfb);
				}
			} else {
				tf.renameTo(tfb);
			}
		}
		try {
			if (tf.createNewFile()) {
				DataOutputStream oos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tf)));

				for (Map.Entry<UUID, Map<Long, short[]>> world : track.entrySet()) {
					HiddenOre.getPlugin().getLogger().info("Saving world - " + world.getKey());
					oos.writeUTF(world.getKey().toString());
					long ccnt = 0l;
					for (Map.Entry<Long, short[]> chunk : world.getValue().entrySet()) {
						if (Config.isDebug) {
							HiddenOre.getPlugin().getLogger().info(" Saving chunk " + chunk.getKey());
						}
						oos.writeBoolean(true);
						oos.writeLong(chunk.getKey());
						short[] layers = chunk.getValue();
						for (short layer : layers) {
							oos.writeShort(layer);
						}
						ccnt++;
					}
					HiddenOre.getPlugin().getLogger().info(" Saved " + ccnt + " chunks");
					oos.writeBoolean(false);
				}
				oos.flush();
				oos.close();
			} else {
				HiddenOre.getPlugin().getLogger().log(Level.WARNING, "Failed to create break tracking save file.");
			}
		} catch (IOException ioe) {
			HiddenOre.getPlugin().getLogger().log(Level.SEVERE, "Failed to save break tracking.", ioe);
		}
		s = System.currentTimeMillis() - s;
		HiddenOre.getPlugin().getLogger().info("Took " + s + "ms to save Break Tracking");
	}

	public void saveMap() {
		long s = System.currentTimeMillis();
		HiddenOre.getPlugin().getLogger().info("Starting Break Map save");
		File tf = Config.getMapFile();
		if (tf.exists()) {
			File tfb = new File(tf.getAbsoluteFile() + ".backup");
			if (tfb.exists()) {
				if (!tfb.delete()) {
					HiddenOre.getPlugin().getLogger().info("Couldn't remove old map backup file - " + tfb);
				} else {
					tf.renameTo(tfb);
				}
			} else {
				tf.renameTo(tfb);
			}
		}
		try {
			if (tf.createNewFile()) {
				DataOutputStream oos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tf)));

				for (Map.Entry<UUID, Map<Long, long[][][]>> world : map.entrySet()) {
					HiddenOre.getPlugin().getLogger().info("Saving world - " + world.getKey());
					oos.writeUTF(world.getKey().toString());
					long ccnt = 0l;
					for (Map.Entry<Long, long[][][]> chunk : world.getValue().entrySet()) {
						if (Config.isDebug) {
							HiddenOre.getPlugin().getLogger().info(" Saving chunk " + chunk.getKey());
						}
						oos.writeBoolean(true);
						oos.writeLong(chunk.getKey());
						long[][][] layers = chunk.getValue();
						for (long[] layer : layers[MAP]) {
							for (long quad : layer) {
								oos.writeLong(quad);
							}
						}
						for (long[] layer : layers[GEN]) {
							for (long quad : layer) {
								oos.writeLong(quad);
							}
						}
						ccnt++;
					}
					HiddenOre.getPlugin().getLogger().info(" Saved " + ccnt + " chunks");
					oos.writeBoolean(false);
				}
				oos.flush();
				oos.close();
			} else {
				HiddenOre.getPlugin().getLogger().log(Level.WARNING, "Failed to create break map save file.");
			}
		} catch (IOException ioe) {
			HiddenOre.getPlugin().getLogger().log(Level.SEVERE, "Failed to save break map.", ioe);
		}
		s = System.currentTimeMillis() - s;
		HiddenOre.getPlugin().getLogger().info("Took " + s + "ms to save Break Map");
	}

	/**
	 * This has a specific purpose of tracking a generation of an ore. For breaks or manipulations, use trackBreak.
	 * 
	 * @param loc
	 * @return
	 */
	public boolean trackGen(Location loc) {
		long s = System.currentTimeMillis();
		int Y = loc.getBlockY();
		int X = (loc.getBlockX() % 16 + 16) % 16;
		int Z = (loc.getBlockZ() % 16 + 16) % 16;
		UUID world = loc.getWorld().getUID();
		Chunk chunk = loc.getChunk();
		long chunk_id = ((long) chunk.getX() << 32L) + (long) chunk.getZ();
		int block_id = (( X << 4) + Z);
		int quad_id = ( block_id / 64);
		long mask_id = (1l << (block_id % 64));
		Map<Long, long[][][]> mapChunks = map.get(world);
		if (mapChunks == null) { // init map chunk
			mapChunks = new HashMap<Long, long[][][]>();
			map.put(world, mapChunks);
		}
		
		long[][][] mapLayers = mapChunks.get(chunk_id);
		if (mapLayers == null) { // init layers
			mapLayers = new long[2][256][4];
			mapChunks.put(chunk_id, mapLayers);
			
			for (int y = 0; y < 256; y++) {
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						Block b = chunk.getBlock(x, y, z);
						if (b.isEmpty() || b.isLiquid()) {
							int bloc = (( x << 4) + z);
							int quad = (block_id / 64);
							long mask = (1l << (bloc % 64));
							mapLayers[MAP][y][quad] |= mask; // if unset, set. Ignore complements basis.
							mapLayers[GEN][y][quad] |= mask; // tracks for "breaks" and "gens".
						}
					}
				}
			}
		}

		boolean ret = false;
		if ((mapLayers[GEN][Y][quad_id] & mask_id) == mask_id) {
			ret = false; // already broken!
		} else {
			ret = true; // new break according to this tracking.
			mapLayers[MAP][Y][quad_id] |= mask_id;
			mapLayers[GEN][Y][quad_id] |= mask_id;
		}
		
		s = System.currentTimeMillis() - s;
		if (s > 10l) {
			HiddenOre.getPlugin().getLogger().info("Took a long time (" + s + "ms) recording generation at " + loc);
		}
		
		return ret;
	}
	
	/**
	 * This has a specific purpose of testing a generation of an ore.
	 * 
	 * @param loc
	 * @return
	 */
	public boolean testGen(Location loc) {
		int Y = loc.getBlockY();
		int X = (loc.getBlockX() % 16 + 16) % 16;
		int Z = (loc.getBlockZ() % 16 + 16) % 16;
		UUID world = loc.getWorld().getUID();
		Chunk chunk = loc.getChunk();
		long chunk_id = ((long) chunk.getX() << 32L) + (long) chunk.getZ();
		int block_id = (( X << 4) + Z);
		int quad_id =  ( block_id / 64);
		long mask_id = (1l << (block_id % 64));

		Map<Long, long[][][]> mapChunks = map.get(world);
		if (mapChunks == null) { // no tracking, so OK 
			return true;
		}
		
		long[][][] mapLayers = mapChunks.get(chunk_id);
		if (mapLayers == null) { // no layer, so OK
			return true;
		}

		if ((mapLayers[GEN][Y][quad_id] & mask_id) == mask_id) {
			return false;
		} else {
			return true;
		}
	}
	
	/**
	 * This is only for map breaks -- "expands" impact of break node, to prevent
	 * any additional breaks of exposed blocks from generating.
	 * 
	 * Somewhat gracefully handles promoting breaks into adjacent chunks if needed.
	 * TODO eval above
	 * 
	 * ATM this works by suppressing _generation_ in blocks around the one that was broken.
	 * It does NOT suppress the ability of one of those blocks to generate more nodes.
	 * 
	 * Basically, once a block is exposed it doesn't transform.
	 * 
	 * @param loc
	 * @param exp TODO
	 * @return
	 */
	public void postTrackBreak(Location loc, boolean exp) {
		long s = System.currentTimeMillis();
		int Y = loc.getBlockY();
		int X = (loc.getBlockX() % 16 + 16) % 16;
		int Z = (loc.getBlockZ() % 16 + 16) % 16;
		UUID world = loc.getWorld().getUID();
		Chunk chunk = loc.getChunk();
		long chunk_id = ((long) chunk.getX() << 32L) + (long) chunk.getZ();
		int block_id = (( X << 4) + Z);
		int quad_id = ( block_id / 64);
		long mask_id = (1l << (block_id % 64));

		Map<Long, long[][][]> genChunks = map.get(world);
		if (genChunks == null) {
			return; // should be init'd or this is being improperly called.
		}
		
		long[][][] mapLayers = genChunks.get(chunk_id);
		if (mapLayers == null) {
			return; // should be init'd or this is being improperly called.
		}
		
		mapLayers[GEN][Y][quad_id] |= mask_id;
		if (exp) {
			if (Y > 0) mapLayers[GEN][Y-1][quad_id] |= mask_id;
			if (Y < 255) mapLayers[GEN][Y+1][quad_id] |= mask_id;
			
			if (X > 0) {
				int nblock_id = (((X-1) << 4) + Z);
				int nquad_id = (nblock_id / 64);
				long nmask_id = (1l << (nblock_id % 64));
				mapLayers[GEN][Y][nquad_id] |= nmask_id;
			} else postTrackBreak(loc.clone().add(-1, 0, 0), false);
			if (X < 15)  {
				int nblock_id = (((X+1) << 4) + Z);
				int nquad_id = (nblock_id / 64);
				long nmask_id = (1l << (nblock_id % 64));
				mapLayers[GEN][Y][nquad_id] |= nmask_id;
			} else postTrackBreak(loc.clone().add(1, 0, 0), false);
			if (Z > 0) {
				int nblock_id = (((X) << 4) + (Z-1));
				int nquad_id = (nblock_id / 64);
				long nmask_id = (1l << (nblock_id % 64));
				mapLayers[GEN][Y][nquad_id] |= nmask_id;
			} else postTrackBreak(loc.clone().add(0, 0, -1), false);
			if (Z < 15)  {
				int nblock_id = (((X) << 4) + (Z+1));
				int nquad_id = (nblock_id / 64);
				long nmask_id = (1l << (nblock_id % 64));
				mapLayers[GEN][Y][nquad_id] |= nmask_id;
			} else postTrackBreak(loc.clone().add(0, 0, 1), false);
		}
		if (Config.isDebug) {
			HiddenOre.getPlugin().getLogger()
					.info("now world " + world + " chunk " + chunk_id + " gent " + mapLayers[GEN][Y][quad_id]);
		}
		s = System.currentTimeMillis() - s;
		if (s > 10l) {
			HiddenOre.getPlugin().getLogger().info("Took a long time (" + s + "ms) recording genmap post break at " + loc);
		}
	
	}
	
	/**
	 * Tracks a first order break or manip.
	 * Locks that location from generating new drop opportunities or from transforming.
	 * 
	 * @param loc
	 * @return
	 */
	public boolean trackBreak(Location loc) {
		long s = System.currentTimeMillis();
		int Y = loc.getBlockY();
		int X = (loc.getBlockX() % 16 + 16) % 16;
		int Z = (loc.getBlockZ() % 16 + 16) % 16;
		UUID world = loc.getWorld().getUID();
		Chunk chunk = loc.getChunk();
		long chunk_id = ((long) chunk.getX() << 32L) + (long) chunk.getZ();
		int block_id = ((X << 4) + Z);
		int quad_id = (block_id / 64);
		long mask_id = (1l << (block_id % 64));
		
		Map<Long, short[]> chunks = track.get(world);
		if (chunks == null) { // init chunk
			chunks = new HashMap<Long, short[]>();
			track.put(world, chunks);
		}
		
		Map<Long, long[][][]> mapChunks = map.get(world);
		if (mapChunks == null) { // init map chunk
			mapChunks = new HashMap<Long, long[][][]>();
			map.put(world, mapChunks);
		}

		short[] layers = chunks.get(chunk_id);
		if (layers == null) { // init layers
			layers = new short[256];
			chunks.put(chunk_id, layers);
		}
		
		long[][][] mapLayers = mapChunks.get(chunk_id);
		if (mapLayers == null) { // init layers
			mapLayers = new long[2][256][4];
			mapChunks.put(chunk_id, mapLayers);
			
			for (int y = 0; y < 256; y++) {
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						Block b = chunk.getBlock(x, y, z);
						if (b.isEmpty() || b.isLiquid()) {
							int bloc = ((x << 4) + z);
							int quad = (block_id / 64);
							long mask = (1l << (bloc % 64));
							mapLayers[MAP][y][quad] |= mask; // if unset, set. Ignore complements basis.
							mapLayers[GEN][y][quad] |= mask; // tracks for "breaks" and "gens".
						}
					}
				}
			}
		}

		if (layers[Y] == 0) {
			// quick layer scan for air and water
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					Block b = chunk.getBlock(x, Y, z);
					if (b.isEmpty() || b.isLiquid()) {
						layers[Y]++;
					}
				}
			}
		}

		boolean ret = true;
		if ((mapLayers[MAP][Y][quad_id] & mask_id) == mask_id) {
			ret = false; // already broken!
		} else {
			ret = true; // new break according to this tracking.
			mapLayers[MAP][Y][quad_id] |= mask_id;
			mapLayers[GEN][Y][quad_id] |= mask_id;
		}
		
		if (layers[Y] >= 256) { // done
			ret = false;
		} else if (ret) {
			layers[Y]++; // represent new break in layer.
			ret = true;
		}
		if (ret) { 
			boolean shallow = false;
			int j = recentPtr;
			for (int i = 0; i < RECENT_MAX; i++) {
				j = (recentPtr + i) % RECENT_MAX;
				if (recent[j] == null) break; // anything null means we're done here
				if (loc.equals(recent[j])) {
					ret = false;
					shallow = true;
					break;
				}
			}
			
			if (!shallow) { // we add to the "end" of a circular list. 
				if (--recentPtr < 0) {
					recentPtr += RECENT_MAX;
				}
				recent[recentPtr] = loc;
			}
		}
		
		if (Config.isDebug) {
			HiddenOre.getPlugin().getLogger()
					.info("now world " + world + " chunk " + chunk_id + " layersum " + layers[Y] + " map" + quad_id + ":" + mask_id + "t " + mapLayers[MAP][Y][quad_id]);
		}

		s = System.currentTimeMillis() - s;
		if (s > 10l) {
			HiddenOre.getPlugin().getLogger().info("Took a long time (" + s + "ms) recording break at " + loc);
		}

		return ret;
	}
}
