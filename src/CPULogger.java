public class CPULogger {

    public static void log(Registers reg, MMU mmu) {
        int pc = reg.pc & 0xFFFF;
        int op0 = mmu.readByte(pc);
        int op1 = mmu.readByte(pc + 1);
        int op2 = mmu.readByte(pc + 2);
        int op3 = mmu.readByte(pc + 3);


        System.out.printf(
            "A:%02X F:%02X B:%02X C:%02X D:%02X E:%02X H:%02X L:%02X SP:%04X PC:%04X (%02X %02X %02X %02X)%n",
            reg.a & 0xFF,
            reg.f & 0xFF,
            reg.b & 0xFF,
            reg.c & 0xFF,
            reg.d & 0xFF,
            reg.e & 0xFF,
            reg.h & 0xFF,
            reg.l & 0xFF,
            reg.sp & 0xFFFF,
            pc,
            op0, op1, op2, op3
        );
    }
}