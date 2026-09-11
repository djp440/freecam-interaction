package local.freecaminteraction.item;

/**
 * 法杖升级核心通用接口。
 * 核心类型通过 {@link #getCoreId()} 进行唯一标识与同杖防重复校验。
 */
public interface IWandCore {
    /**
     * 获取核心唯一标识符（例如 "blueprint"）。
     * 同一法杖的升级槽内不允许存在具有相同 coreId 的多个核心。
     */
    String getCoreId();
}
