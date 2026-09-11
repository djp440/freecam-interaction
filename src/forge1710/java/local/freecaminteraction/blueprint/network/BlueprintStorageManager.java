package local.freecaminteraction.blueprint.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import local.freecaminteraction.ModLog;
import local.freecaminteraction.blueprint.BlueprintBlockEntry;
import local.freecaminteraction.blueprint.BlueprintData;
import local.freecaminteraction.blueprint.BlueprintPartEntry;
import local.freecaminteraction.blueprint.MaterialRequirement;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * 蓝图 NBT 序列化与持久化存储管理。
 * 存储路径：服务器存档目录下的 freecam_interaction/blueprints/<player_uuid>/<bp_id>.dat
 */
public final class BlueprintStorageManager {
    private static File worldSaveDir;
    // 内存缓存加速列表与数据读取
    private static final ConcurrentHashMap<UUID, ConcurrentHashMap<String, BlueprintData>> MEMORY_CACHE =
            new ConcurrentHashMap<UUID, ConcurrentHashMap<String, BlueprintData>>();

    private BlueprintStorageManager() {}

    public static void setSaveDirectory(File dir) {
        worldSaveDir = dir;
    }

    public static File getSaveDirectory() {
        if (worldSaveDir == null) {
            worldSaveDir = new File(".");
        }
        return worldSaveDir;
    }

    private static File getPlayerFolder(UUID playerUuid) {
        File base = new File(getSaveDirectory(), "freecam_interaction" + File.separator + "blueprints" + File.separator + playerUuid.toString());
        if (!base.exists()) {
            base.mkdirs();
        }
        return base;
    }

    /**
     * 保存玩家个人蓝图到磁盘并更新内存缓存。使用临时文件原子写入防损坏。
     */
    public static boolean saveBlueprint(BlueprintData data) {
        if (data == null || data.getAuthorUuid() == null || data.getId() == null || data.getId().isEmpty()) {
            return false;
        }
        UUID author = data.getAuthorUuid();
        File folder = getPlayerFolder(author);
        File targetFile = new File(folder, data.getId() + ".dat");
        File tempFile = new File(folder, data.getId() + ".tmp");

        try {
            NBTTagCompound tag = toNBT(data);
            byte[] bytes = CompressedStreamTools.compress(tag);

            FileOutputStream fos = new FileOutputStream(tempFile);
            try {
                fos.write(bytes);
                fos.flush();
            } finally {
                fos.close();
            }

            if (targetFile.exists()) {
                targetFile.delete();
            }
            if (!tempFile.renameTo(targetFile)) {
                // 如果 renameTo 失败，尝试拷贝覆盖
                FileOutputStream dest = new FileOutputStream(targetFile);
                FileInputStream src = new FileInputStream(tempFile);
                try {
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = src.read(buf)) > 0) {
                        dest.write(buf, 0, len);
                    }
                } finally {
                    src.close();
                    dest.close();
                    tempFile.delete();
                }
            }

            // 更新缓存
            ConcurrentHashMap<String, BlueprintData> map = MEMORY_CACHE.get(author);
            if (map == null) {
                map = new ConcurrentHashMap<String, BlueprintData>();
                MEMORY_CACHE.put(author, map);
            }
            map.put(data.getId(), data);
            return true;
        } catch (Throwable error) {
            ModLog.info("Failed to save blueprint: " + data.getId() + "; err=" + error.getMessage());
            return false;
        }
    }

    /**
     * 读取指定蓝图。优先走缓存，未命中则读磁盘。
     */
    public static BlueprintData loadBlueprint(UUID playerUuid, String bpId) {
        if (playerUuid == null || bpId == null || bpId.isEmpty()) {
            return null;
        }
        ConcurrentHashMap<String, BlueprintData> map = MEMORY_CACHE.get(playerUuid);
        if (map != null && map.containsKey(bpId)) {
            return map.get(bpId);
        }

        File file = new File(getPlayerFolder(playerUuid), bpId + ".dat");
        if (!file.exists() || !file.isFile()) {
            return null;
        }

        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] bytes = new byte[(int) file.length()];
            try {
                int read = fis.read(bytes);
                if (read < bytes.length) {
                    return null;
                }
            } finally {
                fis.close();
            }
            NBTTagCompound tag = CompressedStreamTools.func_152457_a(bytes, net.minecraft.nbt.NBTSizeTracker.field_152451_a);
            BlueprintData data = fromNBT(tag);
            if (data != null) {
                if (map == null) {
                    map = new ConcurrentHashMap<String, BlueprintData>();
                    MEMORY_CACHE.put(playerUuid, map);
                }
                map.put(data.getId(), data);
            }
            return data;
        } catch (Throwable error) {
            ModLog.info("Failed to load blueprint file: " + file.getName() + "; err=" + error.getMessage());
            return null;
        }
    }

    /**
     * 获取玩家全部个人蓝图列表（元数据/数据）。
     */
    public static List<BlueprintData> getPlayerBlueprints(UUID playerUuid) {
        if (playerUuid == null) {
            return Collections.emptyList();
        }
        File folder = getPlayerFolder(playerUuid);
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }

        List<BlueprintData> list = new ArrayList<BlueprintData>();
        for (File f : files) {
            if (f.isFile() && f.getName().endsWith(".dat")) {
                String id = f.getName().substring(0, f.getName().length() - 4);
                BlueprintData data = loadBlueprint(playerUuid, id);
                if (data != null) {
                    list.add(data);
                }
            }
        }
        return list;
    }

    /**
     * 删除指定蓝图文件及内存缓存。
     */
    public static boolean deleteBlueprint(UUID playerUuid, String bpId) {
        if (playerUuid == null || bpId == null || bpId.isEmpty()) {
            return false;
        }
        ConcurrentHashMap<String, BlueprintData> map = MEMORY_CACHE.get(playerUuid);
        if (map != null) {
            map.remove(bpId);
        }
        File file = new File(getPlayerFolder(playerUuid), bpId + ".dat");
        if (file.exists() && file.isFile()) {
            return file.delete();
        }
        return false;
    }

    /**
     * 蓝图转 NBTTagCompound
     */
    public static NBTTagCompound toNBT(BlueprintData data) {
        if (data == null) return new NBTTagCompound();
        NBTTagCompound root = new NBTTagCompound();
        root.setString("id", data.getId() != null ? data.getId() : "");
        root.setString("name", data.getName() != null ? data.getName() : "");
        if (data.getAuthorUuid() != null) {
            root.setString("author", data.getAuthorUuid().toString());
        }
        root.setInteger("sx", data.getSizeX());
        root.setInteger("sy", data.getSizeY());
        root.setInteger("sz", data.getSizeZ());
        root.setInteger("ox", data.getOriginOffsetX());
        root.setInteger("oy", data.getOriginOffsetY());
        root.setInteger("oz", data.getOriginOffsetZ());
        root.setLong("time", data.getCreatedAt());

        // 方块列表
        NBTTagList blockList = new NBTTagList();
        for (BlueprintBlockEntry b : data.getBlockEntries()) {
            if (b == null) continue;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("x", b.getDx());
            tag.setInteger("y", b.getDy());
            tag.setInteger("z", b.getDz());
            tag.setString("block", b.getBlockRegistryName());
            tag.setInteger("meta", b.getMetadata());
            tag.setString("adapter", b.getAdapterId());
            if (b.getTileTag() != null) {
                tag.setTag("tile", b.getTileTag());
            }
            if (!b.getRequiredMaterials().isEmpty()) {
                NBTTagList matList = new NBTTagList();
                for (MaterialRequirement req : b.getRequiredMaterials()) {
                    if (req == null) continue;
                    NBTTagCompound mtag = new NBTTagCompound();
                    mtag.setString("item", req.getItemRegistryName());
                    mtag.setInteger("dmg", req.getDamage());
                    mtag.setInteger("cnt", req.getCount());
                    if (req.getMatchTag() != null) {
                        mtag.setTag("nbt", req.getMatchTag());
                    }
                    matList.appendTag(mtag);
                }
                tag.setTag("mats", matList);
            }
            blockList.appendTag(tag);
        }
        root.setTag("blocks", blockList);

        // 部件列表
        NBTTagList partList = new NBTTagList();
        for (BlueprintPartEntry p : data.getPartEntries()) {
            if (p == null) continue;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("x", p.getDx());
            tag.setInteger("y", p.getDy());
            tag.setInteger("z", p.getDz());
            tag.setInteger("side", p.getSide());
            tag.setString("part", p.getPartId());
            if (p.getConfigTag() != null) {
                tag.setTag("cfg", p.getConfigTag());
            }
            if (p.getRequiredMaterial() != null) {
                MaterialRequirement req = p.getRequiredMaterial();
                NBTTagCompound mtag = new NBTTagCompound();
                mtag.setString("item", req.getItemRegistryName());
                mtag.setInteger("dmg", req.getDamage());
                mtag.setInteger("cnt", req.getCount());
                if (req.getMatchTag() != null) {
                    mtag.setTag("nbt", req.getMatchTag());
                }
                tag.setTag("mat", mtag);
            }
            partList.appendTag(tag);
        }
        root.setTag("parts", partList);

        return root;
    }

    /**
     * NBTTagCompound 反序列化为 BlueprintData
     */
    public static BlueprintData fromNBT(NBTTagCompound root) {
        if (root == null || root.hasNoTags()) return null;
        String id = root.getString("id");
        String name = root.getString("name");
        UUID author = null;
        if (root.hasKey("author")) {
            try {
                author = UUID.fromString(root.getString("author"));
            } catch (Throwable ignored) {}
        }
        int sx = root.getInteger("sx");
        int sy = root.getInteger("sy");
        int sz = root.getInteger("sz");
        int ox = root.getInteger("ox");
        int oy = root.getInteger("oy");
        int oz = root.getInteger("oz");
        long time = root.getLong("time");

        // 方块列表
        List<BlueprintBlockEntry> blocks = new ArrayList<BlueprintBlockEntry>();
        NBTTagList blockList = root.getTagList("blocks", 10);
        for (int i = 0; i < blockList.tagCount(); i++) {
            NBTTagCompound btag = blockList.getCompoundTagAt(i);
            int x = btag.getInteger("x");
            int y = btag.getInteger("y");
            int z = btag.getInteger("z");
            String regName = btag.getString("block");
            int meta = btag.getInteger("meta");
            String adapter = btag.getString("adapter");
            NBTTagCompound tileTag = btag.hasKey("tile") ? btag.getCompoundTag("tile") : null;

            List<MaterialRequirement> mats = new ArrayList<MaterialRequirement>();
            if (btag.hasKey("mats")) {
                NBTTagList mList = btag.getTagList("mats", 10);
                for (int j = 0; j < mList.tagCount(); j++) {
                    NBTTagCompound mtag = mList.getCompoundTagAt(j);
                    String item = mtag.getString("item");
                    int dmg = mtag.getInteger("dmg");
                    int cnt = mtag.getInteger("cnt");
                    NBTTagCompound nbt = mtag.hasKey("nbt") ? mtag.getCompoundTag("nbt") : null;
                    mats.add(new MaterialRequirement(item, dmg, nbt, cnt));
                }
            }

            Block block = (Block) Block.blockRegistry.getObject(regName);
            blocks.add(new BlueprintBlockEntry(x, y, z, regName, block, meta, tileTag, adapter, mats));
        }

        // 部件列表
        List<BlueprintPartEntry> parts = new ArrayList<BlueprintPartEntry>();
        NBTTagList partList = root.getTagList("parts", 10);
        for (int i = 0; i < partList.tagCount(); i++) {
            NBTTagCompound ptag = partList.getCompoundTagAt(i);
            int x = ptag.getInteger("x");
            int y = ptag.getInteger("y");
            int z = ptag.getInteger("z");
            int side = ptag.getInteger("side");
            String partId = ptag.getString("part");
            NBTTagCompound cfg = ptag.hasKey("cfg") ? ptag.getCompoundTag("cfg") : null;

            MaterialRequirement mat = null;
            if (ptag.hasKey("mat")) {
                NBTTagCompound mtag = ptag.getCompoundTag("mat");
                String item = mtag.getString("item");
                int dmg = mtag.getInteger("dmg");
                int cnt = mtag.getInteger("cnt");
                NBTTagCompound nbt = mtag.hasKey("nbt") ? mtag.getCompoundTag("nbt") : null;
                mat = new MaterialRequirement(item, dmg, nbt, cnt);
            }
            parts.add(new BlueprintPartEntry(x, y, z, side, partId, cfg, mat));
        }

        return new BlueprintData(id, name, author, sx, sy, sz, ox, oy, oz, time, blocks, parts);
    }
}
