# Java Game Boy Emulator 🎮

A Lightweight Game Boy (Classic / DMG) emulator written in Java.

## 🚀 Current Features
- **CPU (LR35902):** Core 8-bit Z80-like processor instructions, including extended `0xCB` prefix opcodes, 16-bit stack arithmetic, and interrupt management.
- **MMU (Memory Management Unit):** Full 64KB memory map layout (VRAM, OAM, WRAM, HRAM), hardware timer registers (`DIV`, `TIMA`), and OAM DMA transfer (`0xFF46`).
- **PPU (Pixel Processing Unit):** Background and Window tile rendering pipeline with palette mapping and LCD status management.
- **Display System:** Graphical user interface powered by Java Swing, featuring dynamic scaling and buffered frame rendering.
- **Input System:** Keyboard mapping for directional keypad and action buttons (A, B, Select, Start).

## 📂 Project Structure
```text
gameboy-emulator/
├── bin/          # Compiled Java bytecode (.class files)
├── roms/         # Game Boy ROMs and test suites (.gb)
├── src/          # Source code
│   ├── App.java
│   ├── BitUtils.java
│   ├── CPU.java
│   ├── Display.java
│   ├── MMU.java
│   └── PPU.java
├── .gitignore
└── README.md