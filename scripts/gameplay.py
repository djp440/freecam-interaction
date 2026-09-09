"""复用 minecraft-gameplay 控制器，仅补充本项目验收所需按键。"""
import sys
import json
from pathlib import Path

sys.path.insert(0, str(Path.home() / ".agents/skills/minecraft-gameplay/runtime"))
import minecraft_control

minecraft_control.KEYS["g"] = (0x22, 0x47, False)
minecraft_control.KEYS["v"] = (0x2F, 0x56, False)

if __name__ == "__main__":
    minecraft_control.MP.freeze_support()
    print(json.dumps(minecraft_control.main(), indent=2))
