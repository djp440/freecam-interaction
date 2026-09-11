package local.freecaminteraction.blueprint.network;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import local.freecaminteraction.ModLog;
import local.freecaminteraction.blueprint.BlueprintData;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 客户端蓝图分片重组装配器。
 * 支持多会话同时重组、超时丢弃与单片超限防护。
 */
public final class BlueprintSliceAssembler {
    private static final long TIMEOUT_MS = 60000L; // 60 秒装配超时

    private static final class AssemblySession {
        final String transferId;
        final int totalSlices;
        final byte[][] slices;
        final long createdAt;
        int receivedCount;

        AssemblySession(String transferId, int totalSlices) {
            this.transferId = transferId;
            this.totalSlices = totalSlices;
            this.slices = new byte[totalSlices][];
            this.createdAt = System.currentTimeMillis();
            this.receivedCount = 0;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > TIMEOUT_MS;
        }

        boolean addSlice(int index, byte[] data) {
            if (index < 0 || index >= totalSlices) return false;
            if (slices[index] == null) {
                slices[index] = data != null ? data : new byte[0];
                receivedCount++;
                return true;
            }
            return false;
        }

        boolean isComplete() {
            return receivedCount >= totalSlices;
        }

        byte[] assemble() {
            int totalBytes = 0;
            for (byte[] slice : slices) {
                if (slice == null) return null;
                totalBytes += slice.length;
            }
            byte[] full = new byte[totalBytes];
            int offset = 0;
            for (byte[] slice : slices) {
                System.arraycopy(slice, 0, full, offset, slice.length);
                offset += slice.length;
            }
            return full;
        }
    }

    private static final ConcurrentHashMap<String, AssemblySession> SESSIONS =
            new ConcurrentHashMap<String, AssemblySession>();

    private BlueprintSliceAssembler() {}

    /**
     * 处理分片到达并尝试完成重组。若已全部分片到达，返回解析后的 BlueprintData，否则返回 null。
     */
    public static BlueprintData handleSlice(PacketBlueprintSlice slicePacket) {
        if (slicePacket == null || slicePacket.transferId == null || slicePacket.transferId.isEmpty()) {
            return null;
        }
        cleanExpired();

        String tid = slicePacket.transferId;
        AssemblySession session = SESSIONS.get(tid);
        if (session == null) {
            if (slicePacket.totalSlices <= 0 || slicePacket.totalSlices > 1000) {
                return null;
            }
            session = new AssemblySession(tid, slicePacket.totalSlices);
            SESSIONS.put(tid, session);
        }

        if (session.totalSlices != slicePacket.totalSlices) {
            return null;
        }

        session.addSlice(slicePacket.sliceIndex, slicePacket.payload);

        if (session.isComplete()) {
            SESSIONS.remove(tid);
            byte[] fullBytes = session.assemble();
            if (fullBytes == null) {
                return null;
            }
            try {
                NBTTagCompound tag = CompressedStreamTools.func_152457_a(fullBytes, NBTSizeTracker.field_152451_a);
                BlueprintData bp = BlueprintStorageManager.fromNBT(tag);
                ModLog.info("Blueprint assembled successfully: transferId=" + tid + "; bp=" + (bp != null ? bp.getName() : "null"));
                return bp;
            } catch (Throwable error) {
                ModLog.info("Failed to parse assembled blueprint bytes: " + error.getMessage());
                return null;
            }
        }
        return null;
    }

    public static void clear(String transferId) {
        if (transferId != null) SESSIONS.remove(transferId);
    }

    private static void cleanExpired() {
        for (String id : SESSIONS.keySet()) {
            AssemblySession s = SESSIONS.get(id);
            if (s != null && s.isExpired()) {
                SESSIONS.remove(id);
            }
        }
    }
}
