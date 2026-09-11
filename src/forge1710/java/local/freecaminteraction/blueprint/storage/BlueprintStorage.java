package local.freecaminteraction.blueprint.storage;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import local.freecaminteraction.ModLog;
import local.freecaminteraction.blueprint.BlueprintBlockEntry;
import local.freecaminteraction.blueprint.BlueprintData;
import local.freecaminteraction.blueprint.BlueprintPartEntry;
import local.freecaminteraction.blueprint.MaterialRequirement;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * 蓝图文件存储服务。
 * 负责分段二进制序列化 (Header, Palette, Blocks, Parts) 与 GZIP 压缩存储。
 * 具备临时文件 .tmp 原子替换写入、坏文件读取报错并重命名为 .corrupt 隔离。
 */
public final class BlueprintStorage {
    /** 文件魔数 'BPFC' (0x42504643) */
    public static final int MAGIC_NUMBER = 0x42504643;
    /** 当前二进制格式版本号 */
    public static final int CURRENT_VERSION = 1;

    private static final Object IO_LOCK = new Object();

    private BlueprintStorage() {}

    /**
     * 获取指定玩家的蓝图存储目录：freecam_interaction/blueprints/<uuid>/
     */
    public static File getStorageDir(World world, UUID playerUuid) {
        if (world == null || playerUuid == null) {
            throw new IllegalArgumentException("World and playerUuid must not be null");
        }
        File worldDir = world.getSaveHandler().getWorldDirectory();
        File baseDir = new File(worldDir, "freecam_interaction");
        File blueprintsDir = new File(baseDir, "blueprints");
        File playerDir = new File(blueprintsDir, playerUuid.toString());
        if (!playerDir.exists()) {
            playerDir.mkdirs();
        }
        return playerDir;
    }

    /**
     * 将蓝图保存至磁盘（原子替换写入 .tmp -> .bp）。
     */
    public static boolean saveBlueprint(World world, BlueprintData data) {
        if (world == null || data == null || data.getAuthorUuid() == null) {
            return false;
        }
        String bpId = data.getId();
        if (bpId == null || bpId.trim().isEmpty()) {
            return false;
        }

        synchronized (IO_LOCK) {
            File playerDir = getStorageDir(world, data.getAuthorUuid());
            File targetFile = new File(playerDir, bpId + ".bp");
            File tempFile = new File(playerDir, bpId + ".bp.tmp");

            try {
                // 1. 序列化至临时文件并刷盘
                FileOutputStream fos = new FileOutputStream(tempFile);
                try {
                    BufferedOutputStream bos = new BufferedOutputStream(fos);
                    GZIPOutputStream gzos = new GZIPOutputStream(bos);
                    DataOutputStream out = new DataOutputStream(gzos);

                    writeBlueprint(data, out);
                    out.flush();
                    gzos.finish();
                    bos.flush();
                    fos.getFD().sync();
                } finally {
                    fos.close();
                }

                // 2. 原子替换正式文件
                Files.move(tempFile.toPath(), targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return true;
            } catch (Throwable error) {
                ModLog.info("Failed to save blueprint id=" + bpId + ": " + error.getMessage());
                if (tempFile.exists()) {
                    tempFile.delete();
                }
                return false;
            }
        }
    }

    /**
     * 从磁盘加载指定蓝图。
     * 若文件损坏则重命名为 .corrupt 隔离，不覆盖原数据。
     */
    public static BlueprintData loadBlueprint(World world, UUID playerUuid, String bpId) {
        if (world == null || playerUuid == null || bpId == null || bpId.trim().isEmpty()) {
            return null;
        }

        synchronized (IO_LOCK) {
            File playerDir = getStorageDir(world, playerUuid);
            File bpFile = new File(playerDir, bpId + ".bp");
            if (!bpFile.exists() || !bpFile.isFile()) {
                return null;
            }

            try {
                byte[] rawBytes = Files.readAllBytes(bpFile.toPath());
                return deserializeBlueprint(rawBytes);
            } catch (Throwable error) {
                ModLog.info("Corrupt blueprint detected at " + bpFile.getAbsolutePath() + ": " + error.getMessage());
                // 重命名为 .corrupt 隔离
                File corruptFile = new File(playerDir, bpId + ".bp.corrupt_" + System.currentTimeMillis());
                try {
                    Files.move(bpFile.toPath(), corruptFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    ModLog.info("Isolated corrupt blueprint to " + corruptFile.getName());
                } catch (Throwable moveError) {
                    ModLog.info("Failed to isolate corrupt file: " + moveError.getMessage());
                }
                return null;
            }
        }
    }

    /**
     * 列出玩家目录下的所有有效蓝图。
     */
    public static List<BlueprintData> listBlueprints(World world, UUID playerUuid) {
        if (world == null || playerUuid == null) {
            return Collections.emptyList();
        }

        synchronized (IO_LOCK) {
            File playerDir = getStorageDir(world, playerUuid);
            File[] files = playerDir.listFiles((dir, name) -> name.endsWith(".bp"));
            if (files == null || files.length == 0) {
                return Collections.emptyList();
            }

            List<BlueprintData> list = new ArrayList<BlueprintData>(files.length);
            for (File file : files) {
                String fileName = file.getName();
                String bpId = fileName.substring(0, fileName.length() - 3);
                BlueprintData data = loadBlueprint(world, playerUuid, bpId);
                if (data != null) {
                    list.add(data);
                }
            }
            return Collections.unmodifiableList(list);
        }
    }

    /**
     * 删除指定蓝图文件。
     */
    public static boolean deleteBlueprint(World world, UUID playerUuid, String bpId) {
        if (world == null || playerUuid == null || bpId == null || bpId.trim().isEmpty()) {
            return false;
        }

        synchronized (IO_LOCK) {
            File playerDir = getStorageDir(world, playerUuid);
            File bpFile = new File(playerDir, bpId + ".bp");
            if (bpFile.exists() && bpFile.isFile()) {
                return bpFile.delete();
            }
            return false;
        }
    }

    /**
     * 序列化 BlueprintData 为压缩字节数组（用于快照传输与任务存储）。
     */
    public static byte[] serializeBlueprint(BlueprintData data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos);
             DataOutputStream dos = new DataOutputStream(gzos)) {
            writeBlueprint(data, dos);
            dos.flush();
            gzos.finish();
        }
        return baos.toByteArray();
    }

    /**
     * 反序列化压缩字节数组为不可变的 BlueprintData 快照。
     */
    public static BlueprintData deserializeBlueprint(byte[] compressedBytes) throws IOException {
        if (compressedBytes == null || compressedBytes.length == 0) {
            throw new IOException("Empty compressed blueprint payload");
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedBytes);
             GZIPInputStream gzis = new GZIPInputStream(bais);
             DataInputStream dis = new DataInputStream(gzis)) {
            return readBlueprint(dis);
        }
    }

    /**
     * 分段写入二进制结构：Header, Palette, Blocks, Parts。
     */
    public static void writeBlueprint(BlueprintData data, DataOutput out) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("BlueprintData must not be null");
        }

        // --- 1. Header ---
        out.writeInt(MAGIC_NUMBER);
        out.writeInt(CURRENT_VERSION);
        writeUtf(out, data.getId());
        writeUtf(out, data.getName());
        UUID author = data.getAuthorUuid();
        out.writeBoolean(author != null);
        if (author != null) {
            out.writeLong(author.getMostSignificantBits());
            out.writeLong(author.getLeastSignificantBits());
        }
        out.writeInt(data.getSizeX());
        out.writeInt(data.getSizeY());
        out.writeInt(data.getSizeZ());
        out.writeInt(data.getOriginOffsetX());
        out.writeInt(data.getOriginOffsetY());
        out.writeInt(data.getOriginOffsetZ());
        out.writeLong(data.getCreatedAt());

        // --- 2. Palette (调色板) ---
        List<String> palette = new ArrayList<String>();
        Map<String, Integer> paletteMap = new HashMap<String, Integer>();

        for (BlueprintBlockEntry block : data.getBlockEntries()) {
            getOrAddPalette(palette, paletteMap, block.getBlockRegistryName());
            getOrAddPalette(palette, paletteMap, block.getAdapterId());
            for (MaterialRequirement req : block.getRequiredMaterials()) {
                getOrAddPalette(palette, paletteMap, req.getItemRegistryName());
            }
        }
        for (BlueprintPartEntry part : data.getPartEntries()) {
            getOrAddPalette(palette, paletteMap, part.getPartId());
            if (part.getRequiredMaterial() != null) {
                getOrAddPalette(palette, paletteMap, part.getRequiredMaterial().getItemRegistryName());
            }
        }

        out.writeInt(palette.size());
        for (String str : palette) {
            writeUtf(out, str);
        }

        // --- 3. Block Entries ---
        List<BlueprintBlockEntry> blocks = data.getBlockEntries();
        out.writeInt(blocks.size());
        for (BlueprintBlockEntry block : blocks) {
            out.writeInt(block.getDx());
            out.writeInt(block.getDy());
            out.writeInt(block.getDz());
            out.writeInt(paletteMap.get(block.getBlockRegistryName()));
            out.writeInt(block.getMetadata());
            writeNbt(out, block.getTileTag());
            out.writeInt(paletteMap.get(block.getAdapterId()));

            List<MaterialRequirement> mats = block.getRequiredMaterials();
            out.writeInt(mats.size());
            for (MaterialRequirement mat : mats) {
                writeMaterialRequirement(out, mat, paletteMap);
            }
        }

        // --- 4. Part Entries ---
        List<BlueprintPartEntry> parts = data.getPartEntries();
        out.writeInt(parts.size());
        for (BlueprintPartEntry part : parts) {
            out.writeInt(part.getDx());
            out.writeInt(part.getDy());
            out.writeInt(part.getDz());
            out.writeByte(part.getSide());
            out.writeInt(paletteMap.get(part.getPartId()));
            writeNbt(out, part.getConfigTag());
            MaterialRequirement req = part.getRequiredMaterial();
            out.writeBoolean(req != null);
            if (req != null) {
                writeMaterialRequirement(out, req, paletteMap);
            }
        }
    }

    /**
     * 分段读取二进制结构。
     */
    public static BlueprintData readBlueprint(DataInput in) throws IOException {
        // --- 1. Header ---
        int magic = in.readInt();
        if (magic != MAGIC_NUMBER) {
            throw new IOException("Invalid magic number: 0x" + Integer.toHexString(magic));
        }
        int version = in.readInt();
        if (version != CURRENT_VERSION) {
            throw new IOException("Unsupported blueprint format version: " + version);
        }

        String id = readUtf(in);
        String name = readUtf(in);
        UUID author = null;
        if (in.readBoolean()) {
            long most = in.readLong();
            long least = in.readLong();
            author = new UUID(most, least);
        }
        int sizeX = in.readInt();
        int sizeY = in.readInt();
        int sizeZ = in.readInt();
        int originOffsetX = in.readInt();
        int originOffsetY = in.readInt();
        int originOffsetZ = in.readInt();
        long createdAt = in.readLong();

        // --- 2. Palette ---
        int paletteSize = in.readInt();
        if (paletteSize < 0 || paletteSize > 100000) {
            throw new IOException("Invalid palette size: " + paletteSize);
        }
        List<String> palette = new ArrayList<String>(paletteSize);
        for (int i = 0; i < paletteSize; i++) {
            palette.add(readUtf(in));
        }

        // --- 3. Block Entries ---
        int blockCount = in.readInt();
        if (blockCount < 0 || blockCount > 10000000) {
            throw new IOException("Invalid block count: " + blockCount);
        }
        List<BlueprintBlockEntry> blocks = new ArrayList<BlueprintBlockEntry>(blockCount);
        for (int i = 0; i < blockCount; i++) {
            int dx = in.readInt();
            int dy = in.readInt();
            int dz = in.readInt();
            int blockNameIdx = in.readInt();
            String blockName = getPaletteString(palette, blockNameIdx);
            int metadata = in.readInt();
            NBTTagCompound tileTag = readNbt(in);
            int adapterIdx = in.readInt();
            String adapterId = getPaletteString(palette, adapterIdx);

            int matCount = in.readInt();
            List<MaterialRequirement> mats = new ArrayList<MaterialRequirement>(matCount);
            for (int m = 0; m < matCount; m++) {
                mats.add(readMaterialRequirement(in, palette));
            }

            blocks.add(new BlueprintBlockEntry(dx, dy, dz, blockName, null, metadata, tileTag, adapterId, mats));
        }

        // --- 4. Part Entries ---
        int partCount = in.readInt();
        if (partCount < 0 || partCount > 1000000) {
            throw new IOException("Invalid part count: " + partCount);
        }
        List<BlueprintPartEntry> parts = new ArrayList<BlueprintPartEntry>(partCount);
        for (int i = 0; i < partCount; i++) {
            int dx = in.readInt();
            int dy = in.readInt();
            int dz = in.readInt();
            int side = in.readByte() & 0xFF;
            int partIdIdx = in.readInt();
            String partId = getPaletteString(palette, partIdIdx);
            NBTTagCompound configTag = readNbt(in);
            MaterialRequirement req = null;
            if (in.readBoolean()) {
                req = readMaterialRequirement(in, palette);
            }
            parts.add(new BlueprintPartEntry(dx, dy, dz, side, partId, configTag, req));
        }

        return new BlueprintData(id, name, author, sizeX, sizeY, sizeZ,
                originOffsetX, originOffsetY, originOffsetZ, createdAt, blocks, parts);
    }

    private static int getOrAddPalette(List<String> palette, Map<String, Integer> paletteMap, String str) {
        if (str == null) str = "";
        Integer idx = paletteMap.get(str);
        if (idx == null) {
            idx = palette.size();
            palette.add(str);
            paletteMap.put(str, idx);
        }
        return idx;
    }

    private static String getPaletteString(List<String> palette, int index) throws IOException {
        if (index < 0 || index >= palette.size()) {
            throw new IOException("Palette index out of bounds: " + index + ", size: " + palette.size());
        }
        return palette.get(index);
    }

    private static void writeMaterialRequirement(DataOutput out, MaterialRequirement mat, Map<String, Integer> paletteMap) throws IOException {
        out.writeInt(paletteMap.get(mat.getItemRegistryName()));
        out.writeInt(mat.getDamage());
        writeNbt(out, mat.getMatchTag());
        out.writeInt(mat.getCount());
    }

    private static MaterialRequirement readMaterialRequirement(DataInput in, List<String> palette) throws IOException {
        int itemIdx = in.readInt();
        String itemName = getPaletteString(palette, itemIdx);
        int damage = in.readInt();
        NBTTagCompound matchTag = readNbt(in);
        int count = in.readInt();
        return new MaterialRequirement(itemName, damage, matchTag, count);
    }

    private static void writeNbt(DataOutput out, NBTTagCompound tag) throws IOException {
        if (tag == null || tag.hasNoTags()) {
            out.writeInt(0);
            return;
        }
        byte[] bytes = CompressedStreamTools.compress(tag);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static NBTTagCompound readNbt(DataInput in) throws IOException {
        int length = in.readInt();
        if (length <= 0) {
            return null;
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return CompressedStreamTools.func_152457_a(bytes, NBTSizeTracker.field_152451_a);
    }

    private static void writeUtf(DataOutput out, String str) throws IOException {
        if (str == null) {
            out.writeShort(-1);
            return;
        }
        byte[] utfBytes = str.getBytes(StandardCharsets.UTF_8);
        out.writeShort(utfBytes.length);
        out.write(utfBytes);
    }

    private static String readUtf(DataInput in) throws IOException {
        short len = in.readShort();
        if (len < 0) {
            return null;
        }
        byte[] utfBytes = new byte[len];
        in.readFully(utfBytes);
        return new String(utfBytes, StandardCharsets.UTF_8);
    }
}