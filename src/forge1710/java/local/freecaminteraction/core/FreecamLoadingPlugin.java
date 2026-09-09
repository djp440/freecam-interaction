package local.freecaminteraction.core;

import java.util.Map;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({"local.freecaminteraction.core"})
public final class FreecamLoadingPlugin implements IFMLLoadingPlugin {
    public String[] getASMTransformerClass() { return new String[] {FreecamTransformer.class.getName()}; }
    public String getModContainerClass() { return null; }
    public String getSetupClass() { return null; }
    public void injectData(Map<String, Object> data) { }
    public String getAccessTransformerClass() { return null; }
}
