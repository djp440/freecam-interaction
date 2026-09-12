package local.freecaminteraction.ae2;

import java.util.Random;
import local.freecaminteraction.FreecamWandRegistry;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class BlockAe2Transmitter extends BlockContainer {
    private IIcon off;
    private IIcon on;

    public BlockAe2Transmitter() {
        super(Material.iron);
        setHardness(3.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
        setCreativeTab(CreativeTabs.tabRedstone);
        setBlockName("freecam_interaction.ae2_transmitter");
        setBlockTextureName("freecam_interaction:ae2_transmitter_off");
    }

    @Override public TileEntity createNewTileEntity(World world, int metadata) {
        return Ae2Integration.newTransmitterTile();
    }

    @Override public Item getItemDropped(int meta, Random random, int fortune) {
        return Item.getItemFromBlock(FreecamWandRegistry.ae2Transmitter);
    }

    @Override @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        off = register.registerIcon("freecam_interaction:ae2_transmitter_off");
        on = register.registerIcon("freecam_interaction:ae2_transmitter_on");
    }

    @Override @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) { return meta == 1 ? on : off; }
}
