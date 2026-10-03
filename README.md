# Game Boy Emulator

A Java emulator for the original Nintendo Game Boy (DMG). It runs Game Boy
ROMs in a desktop window with keyboard input, graphics, and audio.

## Features

- LR35902 CPU with base and CB-prefixed instruction handling
- 64 KB memory map, cartridge banking, timers, interrupts, and OAM DMA
- DMG background, window, and sprite rendering
- Four-channel audio synthesis: two pulse channels, wave, and noise
- Keyboard input and dynamically scaled Swing display
- Console output for internal-clock serial transfers, useful for test ROMs
- Save RAM support for compatible cartridges

## Requirements

- Java Development Kit (JDK) 17 or newer
- A Game Boy ROM file. ROMs are not included with the emulator; use ROMs you
  are legally permitted to run.

## Build and Run

### VS Code

Open the project folder and press **Ctrl+Shift+B** to run the configured
**Build Game Boy Emulator** task. Compiled classes are written to `bin`.

### PowerShell

Run these commands from the project root:

```powershell
javac -d bin src\*.java
java -cp bin Main roms\tetris.gb
```

Replace `roms\tetris.gb` with the path to your ROM. For example, to run the
included CPU instruction test ROM:

```powershell
java -cp bin Main roms\cpu_instrs.gb
```

The test ROM reports its progress and results in the terminal. The emulator
window may not show a game screen for test ROMs.

## Controls

| Game Boy input | Keyboard |
| --- | --- |
| Up / Down / Left / Right | Arrow keys |
| A | Z |
| B | X |
| Start | Enter |
| Select | Backspace |

## Project Layout

```text
.
├── .vscode/      VS Code Java settings and build task
├── bin/          Compiled Java classes (generated)
├── lib/          Optional Java libraries
├── roms/         Local ROMs and save data
└── src/          Java source code
```

## Notes

- Generated `.class` files belong in `bin`. Use `javac -d bin src\*.java`
  when building manually; plain `javac src\*.java` places them beside the
  source files.
- ROMs and save files are local data and should not be committed unless you
  have the rights and intend to share them.
