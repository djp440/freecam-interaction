package local.freecaminteraction.client.gui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import local.freecaminteraction.blueprint.network.BlueprintClientCache;
import local.freecaminteraction.blueprint.network.BlueprintNetwork;
import local.freecaminteraction.blueprint.network.PacketBlueprintListRequest;
import local.freecaminteraction.blueprint.network.PacketBlueprintListResponse;
import local.freecaminteraction.blueprint.network.PacketTaskAction;
import local.freecaminteraction.client.FreecamClient;
import local.freecaminteraction.client.renderer.BlueprintGhostRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

/**
 * 蓝图管理与任务列表 GUI。
 * 包含两个标签页：
 * 1) 个人蓝图列表：显示蓝图名、尺寸、方块数，提供“投放预览”和“删除”按钮；
 * 2) 任务列表：显示当前可见任务列表、主人名、三档他人权限（不可见/可见不可建/可建造）、模式外显示开关；
 *    主人可取消、改权限、改模式外显示；获准协作者可点击“施工/建造”按钮。
 * 界面关闭时不误挖世界，沿用已有的松键解锁逻辑。
 */
@SideOnly(Side.CLIENT)
public class GuiBlueprintManager extends GuiScreen implements BlueprintClientCache.CacheListener {
    public static final int TAB_BLUEPRINTS = 0;
    public static final int TAB_TASKS = 1;

    private int currentTab = TAB_BLUEPRINTS;
    private int scrollOffset = 0;
    private static final int ITEMS_PER_PAGE = 5;

    /** 任务主人行左侧文本可用宽度（其右侧被建造/权限/外显/取消四个按钮占用） */
    private static final int ROW_TEXT_WIDTH = 88;

    private List<PacketBlueprintListResponse.BlueprintSummary> blueprints = new ArrayList<PacketBlueprintListResponse.BlueprintSummary>();
    private List<PacketBlueprintListResponse.TaskSummary> tasks = new ArrayList<PacketBlueprintListResponse.TaskSummary>();

    public GuiBlueprintManager() {
        this(TAB_BLUEPRINTS);
    }

    public GuiBlueprintManager(int initialTab) {
        this.currentTab = initialTab;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        BlueprintClientCache.addListener(this);

        int centerX = this.width / 2;

        // 标签切换按钮
        GuiButton tabBp = new GuiButton(10, centerX - 160, 20, 155, 20, I18n.format("gui.freecam_interaction.bp.tab_blueprints"));
        GuiButton tabTasks = new GuiButton(11, centerX + 5, 20, 155, 20, I18n.format("gui.freecam_interaction.bp.tab_tasks"));
        tabBp.enabled = (currentTab != TAB_BLUEPRINTS);
        tabTasks.enabled = (currentTab != TAB_TASKS);
        this.buttonList.add(tabBp);
        this.buttonList.add(tabTasks);

        // 刷新按钮 & 关闭按钮
        this.buttonList.add(new GuiButton(12, centerX - 160, this.height - 28, 90, 20, I18n.format("gui.freecam_interaction.bp.refresh")));
        this.buttonList.add(new GuiButton(13, centerX + 70, this.height - 28, 90, 20, I18n.format("gui.freecam_interaction.bp.close")));

        // 翻页按钮
        this.buttonList.add(new GuiButton(14, centerX - 55, this.height - 28, 50, 20, "<"));
        this.buttonList.add(new GuiButton(15, centerX + 5, this.height - 28, 50, 20, ">"));

        // 加载列表项按钮
        refreshData();
        buildItemButtons();

        // 首次打开主动请求一次最新列表
        BlueprintNetwork.sendToServer(new PacketBlueprintListRequest((int) (System.currentTimeMillis() % 100000)));
    }

    @Override
    public void onGuiClosed() {
        BlueprintClientCache.removeListener(this);
    }

    @Override
    public boolean doesGuiPauseGame() {
        // 蓝图管理界面必须保持世界与网络 tick 运行，否则服务端包排队到关界面才执行
        return false;
    }

    @Override
    public void onCacheUpdated() {
        refreshData();
        buildItemButtons();
    }

    private void refreshData() {
        this.blueprints = BlueprintClientCache.getPersonalBlueprints();
        this.tasks = new ArrayList<PacketBlueprintListResponse.TaskSummary>(BlueprintClientCache.getAllTasks());
    }

    private void buildItemButtons() {
        // 清理除 10..15 之外的列表行按钮
        List<GuiButton> toKeep = new ArrayList<GuiButton>();
        for (Object obj : this.buttonList) {
            GuiButton b = (GuiButton) obj;
            if (b.id >= 10 && b.id <= 15) {
                toKeep.add(b);
            }
        }
        this.buttonList.clear();
        this.buttonList.addAll(toKeep);

        int centerX = this.width / 2;
        int startY = 50;

        if (currentTab == TAB_BLUEPRINTS) {
            int total = blueprints.size();
            int maxOffset = Math.max(0, total - ITEMS_PER_PAGE);
            if (scrollOffset > maxOffset) scrollOffset = maxOffset;
            if (scrollOffset < 0) scrollOffset = 0;

            for (int i = 0; i < ITEMS_PER_PAGE; i++) {
                int index = scrollOffset + i;
                if (index >= total) break;
                PacketBlueprintListResponse.BlueprintSummary bp = blueprints.get(index);
                int y = startY + i * 32;

                // 投放预览按钮 (id: 100 + i)
                this.buttonList.add(new GuiButton(100 + i, centerX + 40, y + 4, 60, 20, I18n.format("gui.freecam_interaction.bp.preview")));
                // 删除按钮 (id: 200 + i)
                this.buttonList.add(new GuiButton(200 + i, centerX + 105, y + 4, 55, 20, I18n.format("gui.freecam_interaction.bp.delete")));
            }
        } else {
            int total = tasks.size();
            int maxOffset = Math.max(0, total - ITEMS_PER_PAGE);
            if (scrollOffset > maxOffset) scrollOffset = maxOffset;
            if (scrollOffset < 0) scrollOffset = 0;

            for (int i = 0; i < ITEMS_PER_PAGE; i++) {
                int index = scrollOffset + i;
                if (index >= total) break;
                PacketBlueprintListResponse.TaskSummary task = tasks.get(index);
                int y = startY + i * 32;

                if (task.isOwner) {
                    // 主人施工按钮 (id: 600 + i)
                    this.buttonList.add(new GuiButton(600 + i, centerX - 60, y + 4, 72, 20, I18n.format("gui.freecam_interaction.bp.build")));

                    // 主人权限按钮：切换三档他人权限 (id: 300 + i)
                    String permText = getPermShortText(task.permission);
                    this.buttonList.add(new GuiButton(300 + i, centerX + 16, y + 4, 42, 20, permText));

                    // 主人模式外显示开关 (id: 400 + i)
                    String showText = task.showOutside ? I18n.format("gui.freecam_interaction.bp.out_on") : I18n.format("gui.freecam_interaction.bp.out_off");
                    this.buttonList.add(new GuiButton(400 + i, centerX + 62, y + 4, 48, 20, showText));

                    // 主人取消任务 (id: 500 + i)
                    this.buttonList.add(new GuiButton(500 + i, centerX + 114, y + 4, 46, 20, I18n.format("gui.freecam_interaction.bp.cancel_task")));
                } else {
                    // 非主人协作者
                    if (task.permission == 2) { // CAN_BUILD
                        this.buttonList.add(new GuiButton(600 + i, centerX + 80, y + 4, 80, 20, I18n.format("gui.freecam_interaction.bp.build")));
                    }
                }
            }
        }
    }

    private String getPermShortText(byte perm) {
        switch (perm) {
            case 0: return I18n.format("gui.freecam_interaction.bp.perm_hidden");
            case 1: return I18n.format("gui.freecam_interaction.bp.perm_visible");
            case 2: return I18n.format("gui.freecam_interaction.bp.perm_build");
            default: return "";
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) return;

        if (button.id == 10) {
            // 切到个人蓝图
            this.currentTab = TAB_BLUEPRINTS;
            this.scrollOffset = 0;
            initGui();
        } else if (button.id == 11) {
            // 切到任务列表
            this.currentTab = TAB_TASKS;
            this.scrollOffset = 0;
            initGui();
        } else if (button.id == 12) {
            // 刷新
            BlueprintNetwork.sendToServer(new PacketBlueprintListRequest((int) (System.currentTimeMillis() % 100000)));
        } else if (button.id == 13) {
            // 关闭
            this.mc.displayGuiScreen(null);
        } else if (button.id == 14) {
            // 上一页
            if (scrollOffset > 0) {
                scrollOffset--;
                buildItemButtons();
            }
        } else if (button.id == 15) {
            // 下一页
            int total = (currentTab == TAB_BLUEPRINTS) ? blueprints.size() : tasks.size();
            if (scrollOffset + ITEMS_PER_PAGE < total) {
                scrollOffset++;
                buildItemButtons();
            }
        } else if (button.id >= 100 && button.id < 200) {
            // 投放预览
            int idx = scrollOffset + (button.id - 100);
            if (idx < blueprints.size()) {
                PacketBlueprintListResponse.BlueprintSummary bp = blueprints.get(idx);
                BlueprintGhostRenderer.INSTANCE.startPlacementPreview(bp.id, bp.name);
                this.mc.displayGuiScreen(null);
            }
        } else if (button.id >= 200 && button.id < 300) {
            // 删除个人蓝图
            int idx = scrollOffset + (button.id - 200);
            if (idx < blueprints.size()) {
                PacketBlueprintListResponse.BlueprintSummary bp = blueprints.get(idx);
                BlueprintNetwork.sendToServer(PacketTaskAction.deleteBlueprint(bp.id));
            }
        } else if (button.id >= 300 && button.id < 400) {
            // 切换任务权限 0 -> 1 -> 2 -> 0
            int idx = scrollOffset + (button.id - 300);
            if (idx < tasks.size()) {
                PacketBlueprintListResponse.TaskSummary task = tasks.get(idx);
                byte nextPerm = (byte) ((task.permission + 1) % 3);
                BlueprintNetwork.sendToServer(PacketTaskAction.changePermission(task.taskId, nextPerm));
            }
        } else if (button.id >= 400 && button.id < 500) {
            // 切换模式外显示
            int idx = scrollOffset + (button.id - 400);
            if (idx < tasks.size()) {
                PacketBlueprintListResponse.TaskSummary task = tasks.get(idx);
                BlueprintNetwork.sendToServer(PacketTaskAction.toggleShowOutside(task.taskId, !task.showOutside));
            }
        } else if (button.id >= 500 && button.id < 600) {
            // 取消任务
            int idx = scrollOffset + (button.id - 500);
            if (idx < tasks.size()) {
                PacketBlueprintListResponse.TaskSummary task = tasks.get(idx);
                BlueprintNetwork.sendToServer(PacketTaskAction.cancel(task.taskId));
            }
        } else if (button.id >= 600 && button.id < 700) {
            // 施工/建造（任务主人或获准协作者）
            int idx = scrollOffset + (button.id - 600);
            if (idx < tasks.size()) {
                PacketBlueprintListResponse.TaskSummary task = tasks.get(idx);
                BlueprintNetwork.sendToServer(PacketTaskAction.build(task.taskId));
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int centerX = this.width / 2;
        int startY = 50;

        String title = (currentTab == TAB_BLUEPRINTS)
                ? I18n.format("gui.freecam_interaction.bp.title_blueprints")
                : I18n.format("gui.freecam_interaction.bp.title_tasks");
        drawCenteredString(this.fontRendererObj, title, centerX, 6, 0xFFFFFF);

        if (currentTab == TAB_BLUEPRINTS) {
            if (blueprints.isEmpty()) {
                drawCenteredString(this.fontRendererObj, I18n.format("gui.freecam_interaction.bp.empty_blueprints"), centerX, 100, 0x888888);
            } else {
                for (int i = 0; i < ITEMS_PER_PAGE; i++) {
                    int index = scrollOffset + i;
                    if (index >= blueprints.size()) break;
                    PacketBlueprintListResponse.BlueprintSummary bp = blueprints.get(index);
                    int y = startY + i * 32;

                    // 背景条
                    drawRect(centerX - 160, y, centerX + 160, y + 28, 0x80000000);
                    // 蓝图名称与规格
                    this.fontRendererObj.drawStringWithShadow(bp.name, centerX - 154, y + 4, 0xFFFFFF);
                    String sub = bp.sizeX + "x" + bp.sizeY + "x" + bp.sizeZ + " | " + bp.blockCount + " " + I18n.format("gui.freecam_interaction.bp.blocks");
                    this.fontRendererObj.drawString(sub, centerX - 154, y + 16, 0xAAAAAA);
                }
            }
        } else {
            if (tasks.isEmpty()) {
                drawCenteredString(this.fontRendererObj, I18n.format("gui.freecam_interaction.bp.empty_tasks"), centerX, 100, 0x888888);
            } else {
                for (int i = 0; i < ITEMS_PER_PAGE; i++) {
                    int index = scrollOffset + i;
                    if (index >= tasks.size()) break;
                    PacketBlueprintListResponse.TaskSummary task = tasks.get(index);
                    int y = startY + i * 32;

                    // 背景条
                    drawRect(centerX - 160, y, centerX + 160, y + 28, 0x80000000);
                    // 任务名称与主人（主人行按钮占位更宽，文本按可用宽度截断）
                    int textWidth = task.isOwner ? ROW_TEXT_WIDTH : 150;
                    this.fontRendererObj.drawStringWithShadow(this.fontRendererObj.trimStringToWidth(task.blueprintName, textWidth), centerX - 154, y + 4, 0xFFFFFF);
                    String sub = I18n.format("gui.freecam_interaction.bp.owner") + ": " + task.ownerName + " (" + task.anchorX + "," + task.anchorY + "," + task.anchorZ + ")";
                    this.fontRendererObj.drawString(this.fontRendererObj.trimStringToWidth(sub, textWidth), centerX - 154, y + 16, 0xAAAAAA);

                    if (!task.isOwner) {
                        String permDesc = getPermShortText(task.permission);
                        this.fontRendererObj.drawString(permDesc, centerX - 10, y + 10, 0xE6F8F5);
                    }
                }
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
