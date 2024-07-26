
import java.util.HashMap;
import java.util.Map;
import utils.FLAGS6502;



public class CPU6502 {

   // CPU Core registers
   public int a = 0x00; // Accumulator Register
   public int x = 0x00; // X Register
   public int y = 0x00; // Y Register
   public int stkp = 0x00; // Stack Pointer (points to location on bus)
   public int pc = 0x0000; // Program Counter
   public int status = 0x00; // Status Register

   public Bus bus = null; // Linkage to the communications bus

   Instruction[] lookup = new Instruction[256];

   // Assistive variables to facilitate emulation
   int fetched = 0x00; // Represents the working input value to the ALU
   int temp = 0x0000; // A convenience variable used everywhere
   int addr_abs = 0x0000; // All used memory addresses end up in here
   int addr_rel = 0x0000; // Represents absolute address following a branch
   int opcode = 0x00; // Is the instruction int
   int cycles = 0x00; // Counts how many cycles the instruction has remaining
   int clockCount = 0; // A global accumulation of the number of clocks

   public CPU6502() {
       setupLookupTable();
   }
    
   
   // External event functions. In hardware these represent pins that are asserted
	// to produce a change in state.
	void reset(){	// Reset Interrupt - Forces CPU into known state
        pc = ResetVector();

        // Reset internal registers
        a = 0;
        x = 0;
        y = 0;
        stkp = 0xFD;
        status = 0x00 | FLAGS6502.U.getValue();

        // Clear internal helper variables
        addr_rel = 0x0000;
        addr_abs = 0x0000;
        fetched = 0x00;

        // Reset takes time
        cycles = 8;

    
    
    }
    
    void irq(){		// Interrupt Request - Executes an instruction at a specific location
        pushStack((pc >> 8) & 0x00FF);
        pushStack(pc & 0x00FF);

        //Push the current Status Register to the Stack
        SetFlag(FLAGS6502.B, false);
        SetFlag(FLAGS6502.U, true);
        SetFlag(FLAGS6502.I, true);
        pushStack(status);

        //Jump to the NMI Routine specified at 0xFFFA
        pc = irqVector();

        // An Interrupt take 7 cycles
        cycles = 7;

    }
    
    void nmi(){		// Non-Maskable Interrupt Request - As above, but cannot be disabled
        
        pushStack((pc >> 8) & 0x00FF);
        pushStack(pc & 0x00FF);

        //Push the current Status Register to the Stack
        SetFlag(FLAGS6502.B, false);
        SetFlag(FLAGS6502.U, true);
        SetFlag(FLAGS6502.I, true);
        pushStack(status);

        //Jump to the NMI Routine specified at 0xFFFA
        pc = nmiVector();

       //An NMI take 8 cycles
        cycles = 8;
    }
    
    void clock(){	// Perform one clock cycle's worth of update
        if (cycles <= 0) {
        opcode = read(pc);
        SetFlag(FLAGS6502.U, true);
        //Increment the Program Counter
        pc++;
        pc &= 0xFFFF;
        Instruction instr = lookup[opcode];
        cycles = instr.cycles;
        int additional_cycle_1 = instr.addrmode.run();
        int additional_cycle_2 = instr.operate.run();

        cycles += (additional_cycle_1 & additional_cycle_2);
        SetFlag(FLAGS6502.U, true);
        }

        clockCount++;
        cycles--;


    }
	// Indicates the current instruction has completed by returning true. This is
	// a utility function to enable "step-by-step" execution, without manually 
	// clocking every cycle
	boolean complete(){
        return cycles==0;
    }
    
  
    
    private void setupLookupTable() {
        lookup[0x00] = new Instruction( "BRK",  this::BRK,  this::IMM, "IMM", 7 );
        lookup[0x01] = new Instruction( "ORA",  this::ORA,  this::IZX, "IZX", 6);
        lookup[0x02] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 2);
        lookup[0x03] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 8);
        lookup[0x04] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 3);
        lookup[0x05] = new Instruction( "ORA",  this::ORA,  this::ZP0, "ZP0", 3);
        lookup[0x06] = new Instruction( "ASL",  this::ASL,  this::ZP0, "ZP0", 5);
        lookup[0x07] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 5);
        lookup[0x08] = new Instruction( "PHP",  this::PHP,  this::IMP, "IMP", 3);
        lookup[0x09] = new Instruction( "ORA",  this::ORA,  this::IMM, "IMM", 2);
        lookup[0x0a] = new Instruction( "ASL",  this::ASL,  this::IMP, "IMP", 2);
        lookup[0x0b] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 2);
        lookup[0x0c] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 4);
        lookup[0x0d] = new Instruction( "ORA",  this::ORA,  this::ABS, "ABS", 4);
        lookup[0x0e] = new Instruction( "ASL",  this::ASL,  this::ABS, "ABS", 6);
        lookup[0x0f] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 6  );
        lookup[0x10] = new Instruction( "BPL",  this::BPL,  this::REL, "REL", 2);
        lookup[0x11] = new Instruction( "ORA",  this::ORA,  this::IZY, "IZY", 5);
        lookup[0x12] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 2);
        lookup[0x13] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 8);
        lookup[0x14] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 4);
        lookup[0x15] = new Instruction( "ORA",  this::ORA,  this::ZPX, "ZPX", 4);
        lookup[0x16] = new Instruction( "ASL",  this::ASL,  this::ZPX, "ZPX", 6);
        lookup[0x17] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 6);
        lookup[0x18] = new Instruction( "CLC",  this::CLC,  this::IMP, "IMP", 2);
        lookup[0x19] = new Instruction( "ORA",  this::ORA,  this::ABY, "ABY", 4);
        lookup[0x1a] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 2);
        lookup[0x1b] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 7);
        lookup[0x1c] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 4);
        lookup[0x1d] = new Instruction( "ORA",  this::ORA,  this::ABX, "ABX", 4);
        lookup[0x1e] = new Instruction( "ASL",  this::ASL,  this::ABX, "ABX", 7);
        lookup[0x1f] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 7);
        lookup[0x20] = new Instruction( "JSR",  this::JSR,  this::ABS, "ABS", 6);
        lookup[0x21] = new Instruction( "AND",  this::AND,  this::IZX, "IZX", 6);
        lookup[0x22] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 2);
        lookup[0x23] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 8);
        lookup[0x24] = new Instruction( "BIT",  this::BIT,  this::ZP0, "ZP0", 3);
        lookup[0x25] = new Instruction( "AND",  this::AND,  this::ZP0, "ZP0", 3);
        lookup[0x26] = new Instruction( "ROL",  this::ROL,  this::ZP0, "ZP0", 5);
        lookup[0x27] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 5);
        lookup[0x28] = new Instruction( "PLP",  this::PLP,  this::IMP, "IMP", 4);
        lookup[0x29] = new Instruction( "AND",  this::AND,  this::IMM, "IMM", 2);
        lookup[0x2a] = new Instruction( "ROL",  this::ROL,  this::IMP, "IMP", 2);
        lookup[0x2b] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 2);
        lookup[0x2c] = new Instruction( "BIT",  this::BIT,  this::ABS, "ABS", 4);
        lookup[0x2d] = new Instruction( "AND",  this::AND,  this::ABS, "ABS", 4);
        lookup[0x2e] = new Instruction( "ROL",  this::ROL,  this::ABS, "ABS", 6);
        lookup[0x2f] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 6);
        lookup[0x30] = new Instruction( "BMI",  this::BMI,  this::REL, "REL", 2);
        lookup[0x31] = new Instruction( "AND",  this::AND,  this::IZY, "IZY", 5);
        lookup[0x32] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 2);
        lookup[0x33] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 8);
        lookup[0x34] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 4);
        lookup[0x35] = new Instruction( "AND",  this::AND,  this::ZPX, "ZPX", 4);
        lookup[0x36] = new Instruction( "ROL",  this::ROL,  this::ZPX, "ZPX", 6);
        lookup[0x37] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 6);
        lookup[0x38] = new Instruction( "SEC",  this::SEC,  this::IMP, "IMP", 2);
        lookup[0x39] = new Instruction( "AND",  this::AND,  this::ABY, "ABY", 4);
        lookup[0x3a] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 2);
        lookup[0x3b] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 7);
        lookup[0x3c] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 4);
        lookup[0x3d] = new Instruction( "AND",  this::AND,  this::ABX, "ABX", 4);
        lookup[0x3e] = new Instruction( "ROL",  this::ROL,  this::ABX, "ABX", 7);
        lookup[0x3f] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 7);
        lookup[0x40] = new Instruction( "RTI",  this::RTI,  this::IMP, "IMP", 6);
        lookup[0x41] = new Instruction( "EOR",  this::EOR,  this::IZX, "IZX", 6);
        lookup[0x42] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 2);
        lookup[0x43] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 8);
        lookup[0x44] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 3);
        lookup[0x45] = new Instruction( "EOR",  this::EOR,  this::ZP0, "ZP0", 3);
        lookup[0x46] = new Instruction( "LSR",  this::LSR,  this::ZP0, "ZP0", 5);
        lookup[0x47] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 5);
        lookup[0x48] = new Instruction( "PHA",  this::PHA,  this::IMP, "IMP", 3);
        lookup[0x49] = new Instruction( "EOR",  this::EOR,  this::IMM, "IMM", 2);
        lookup[0x4a] = new Instruction( "LSR",  this::LSR,  this::IMP, "IMP", 2);
        lookup[0x4b] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 2);
        lookup[0x4c] = new Instruction( "JMP",  this::JMP,  this::ABS, "ABS", 3);
        lookup[0x4d] = new Instruction( "EOR",  this::EOR,  this::ABS, "ABS", 4);
        lookup[0x4e] = new Instruction( "LSR",  this::LSR,  this::ABS, "ABS", 6);
        lookup[0x4f] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 6);
        lookup[0x50] = new Instruction( "BVC",  this::BVC,  this::REL, "REL", 2);
        lookup[0x51] = new Instruction( "EOR",  this::EOR,  this::IZY, "IZY", 5);
        lookup[0x52] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 2);
        lookup[0x53] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 8);
        lookup[0x54] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 4);
        lookup[0x55] = new Instruction( "EOR",  this::EOR,  this::ZPX, "ZPX", 4);
        lookup[0x56] = new Instruction( "LSR",  this::LSR,  this::ZPX, "ZPX", 6);
        lookup[0x57] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 6);
        lookup[0x58] = new Instruction( "CLI",  this::CLI,  this::IMP, "IMP", 2);
        lookup[0x59] = new Instruction( "EOR",  this::EOR,  this::ABY, "ABY", 4);
        lookup[0x5a] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 2);
        lookup[0x5b] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 7);
        lookup[0x5c] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 4);
        lookup[0x5d] = new Instruction( "EOR",  this::EOR,  this::ABX, "ABX", 4);
        lookup[0x5e] = new Instruction( "LSR",  this::LSR,  this::ABX, "ABX", 7);
        lookup[0x5f] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 7);
        lookup[0x60] = new Instruction( "RTS",  this::RTS,  this::IMP, "IMP", 6);
        lookup[0x61] = new Instruction( "ADC",  this::ADC,  this::IZX, "IZX", 6);
        lookup[0x62] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 2);
        lookup[0x63] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 8);
        lookup[0x64] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 3);
        lookup[0x65] = new Instruction( "ADC",  this::ADC,  this::ZP0, "ZP0", 3);
        lookup[0x66] = new Instruction( "ROR",  this::ROR,  this::ZP0, "ZP0", 5);
        lookup[0x67] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 5);
        lookup[0x68] = new Instruction( "PLA",  this::PLA,  this::IMP, "IMP", 4);
        lookup[0x69] = new Instruction( "ADC",  this::ADC,  this::IMM, "IMM", 2);
        lookup[0x6a] = new Instruction( "ROR",  this::ROR,  this::IMP, "IMP", 2);
        lookup[0x6b] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 2);
        lookup[0x6c] = new Instruction( "JMP",  this::JMP,  this::IND, "IND", 5);
        lookup[0x6d] = new Instruction( "ADC",  this::ADC,  this::ABS, "ABS", 4);
        lookup[0x6e] = new Instruction( "ROR",  this::ROR,  this::ABS, "ABS", 6);
        lookup[0x6f] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 6);
        lookup[0x70] = new Instruction( "BVS",  this::BVS,  this::REL, "REL", 2);
        lookup[0x71] = new Instruction( "ADC",  this::ADC,  this::IZY, "IZY", 5);
        lookup[0x72] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 2);
        lookup[0x73] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 8);
        lookup[0x74] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 4);
        lookup[0x75] = new Instruction( "ADC",  this::ADC,  this::ZPX, "ZPX", 4);
        lookup[0x76] = new Instruction( "ROR",  this::ROR,  this::ZPX, "ZPX", 6);
        lookup[0x77] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 6);
        lookup[0x78] = new Instruction( "SEI",  this::SEI,  this::IMP, "IMP", 2);
        lookup[0x79] = new Instruction( "ADC",  this::ADC,  this::ABY, "ABY", 4);
        lookup[0x7a] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 2);
        lookup[0x7b] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 7);
        lookup[0x7c] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 4);
        lookup[0x7d] = new Instruction( "ADC",  this::ADC,  this::ABX, "ABX", 4);
        lookup[0x7e] = new Instruction( "ROR",  this::ROR,  this::ABX, "ABX", 7);
        lookup[0x7f] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 7);
        lookup[0x80] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 2);
        lookup[0x81] = new Instruction( "STA",  this::STA,  this::IZX, "IZX", 6);
        lookup[0x82] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 2);
        lookup[0x83] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 6);
        lookup[0x84] = new Instruction( "STY",  this::STY,  this::ZP0, "ZP0", 3);
        lookup[0x85] = new Instruction( "STA",  this::STA,  this::ZP0, "ZP0", 3);
        lookup[0x86] = new Instruction( "STX",  this::STX,  this::ZP0, "ZP0", 3);
        lookup[0x87] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 3);
        lookup[0x88] = new Instruction( "DEY",  this::DEY,  this::IMP, "IMP", 2);
        lookup[0x89] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 2);
        lookup[0x8a] = new Instruction( "TXA",  this::TXA,  this::IMP, "IMP", 2);
        lookup[0x8b] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 2);
        lookup[0x8c] = new Instruction( "STY",  this::STY,  this::ABS, "ABS", 4);
        lookup[0x8d] = new Instruction( "STA",  this::STA,  this::ABS, "ABS", 4);
        lookup[0x8e] = new Instruction( "STX",  this::STX,  this::ABS, "ABS", 4);
        lookup[0x8f] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 4);
        lookup[0x90] = new Instruction( "BCC",  this::BCC,  this::REL, "REL", 2);
        lookup[0x91] = new Instruction( "STA",  this::STA,  this::IZY, "IZY", 6);
        lookup[0x92] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 2);
        lookup[0x93] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 6);
        lookup[0x94] = new Instruction( "STY",  this::STY,  this::ZPX, "ZPX", 4);
        lookup[0x95] = new Instruction( "STA",  this::STA,  this::ZPX, "ZPX", 4);
        lookup[0x96] = new Instruction( "STX",  this::STX,  this::ZPY, "ZPY", 4);
        lookup[0x97] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 4);
        lookup[0x98] = new Instruction( "TYA",  this::TYA,  this::IMP, "IMP", 2);
        lookup[0x99] = new Instruction( "STA",  this::STA,  this::ABY, "ABY", 5);
        lookup[0x9a] = new Instruction( "TXS",  this::TXS,  this::IMP, "IMP", 2);
        lookup[0x9b] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 5);
        lookup[0x9c] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 5);
        lookup[0x9d] = new Instruction( "STA",  this::STA,  this::ABX, "ABX", 5);
        lookup[0x9e] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 5);
        lookup[0x9f] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 5);
        lookup[0xa0] = new Instruction( "LDY",  this::LDY,  this::IMM, "IMM", 2);
        lookup[0xa1] = new Instruction( "LDA",  this::LDA,  this::IZX, "IZX", 6);
        lookup[0xa2] = new Instruction( "LDX",  this::LDX,  this::IMM, "IMM", 2);
        lookup[0xa3] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 6);
        lookup[0xa4] = new Instruction( "LDY",  this::LDY,  this::ZP0, "ZP0", 3);
        lookup[0xa5] = new Instruction( "LDA",  this::LDA,  this::ZP0, "ZP0", 3);
        lookup[0xa6] = new Instruction( "LDX",  this::LDX,  this::ZP0, "ZP0", 3);
        lookup[0xa7] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 3);
        lookup[0xa8] = new Instruction( "TAY",  this::TAY,  this::IMP, "IMP", 2);
        lookup[0xa9] = new Instruction( "LDA",  this::LDA,  this::IMM, "IMM", 2);
        lookup[0xaa] = new Instruction( "TAX",  this::TAX,  this::IMP, "IMP", 2);
        lookup[0xab] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 2);
        lookup[0xac] = new Instruction( "LDY",  this::LDY,  this::ABS, "ABS", 4);
        lookup[0xad] = new Instruction( "LDA",  this::LDA,  this::ABS, "ABS", 4);
        lookup[0xae] = new Instruction( "LDX",  this::LDX,  this::ABS, "ABS", 4);
        lookup[0xaf] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 4);
        lookup[0xb0] = new Instruction( "BCS",  this::BCS,  this::REL, "REL", 2);
        lookup[0xb1] = new Instruction( "LDA",  this::LDA,  this::IZY, "IZY", 5);
        lookup[0xb2] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 2);
        lookup[0xb3] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 5);
        lookup[0xb4] = new Instruction( "LDY",  this::LDY,  this::ZPX, "ZPX", 4);
        lookup[0xb5] = new Instruction( "LDA",  this::LDA,  this::ZPX, "ZPX", 4);
        lookup[0xb6] = new Instruction( "LDX",  this::LDX,  this::ZPY, "ZPY", 4);
        lookup[0xb7] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 4);
        lookup[0xb8] = new Instruction( "CLV",  this::CLV,  this::IMP, "IMP", 2);
        lookup[0xb9] = new Instruction( "LDA",  this::LDA,  this::ABY, "ABY", 4);
        lookup[0xba] = new Instruction( "TSX",  this::TSX,  this::IMP, "IMP", 2);
        lookup[0xbb] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 4);
        lookup[0xbc] = new Instruction( "LDY",  this::LDY,  this::ABX, "ABX", 4);
        lookup[0xbd] = new Instruction( "LDA",  this::LDA,  this::ABX, "ABX", 4);
        lookup[0xbe] = new Instruction( "LDX",  this::LDX,  this::ABY, "ABY", 4);
        lookup[0xbf] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 4);
        lookup[0xc0] = new Instruction( "CPY",  this::CPY,  this::IMM, "IMM", 2);
        lookup[0xc1] = new Instruction( "CMP",  this::CMP,  this::IZX, "IZX", 6);
        lookup[0xc2] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 2);
        lookup[0xc3] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 8);
        lookup[0xc4] = new Instruction( "CPY",  this::CPY,  this::ZP0, "ZP0", 3);
        lookup[0xc5] = new Instruction( "CMP",  this::CMP,  this::ZP0, "ZP0", 3);
        lookup[0xc6] = new Instruction( "DEC",  this::DEC,  this::ZP0, "ZP0", 5);
        lookup[0xc7] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 5);
        lookup[0xc8] = new Instruction( "INY",  this::INY,  this::IMP, "IMP", 2);
        lookup[0xc9] = new Instruction( "CMP",  this::CMP,  this::IMM, "IMM", 2);
        lookup[0xca] = new Instruction( "DEX",  this::DEX,  this::IMP, "IMP", 2);
        lookup[0xcb] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 2);
        lookup[0xcc] = new Instruction( "CPY",  this::CPY,  this::ABS, "ABS", 4);
        lookup[0xcd] = new Instruction( "CMP",  this::CMP,  this::ABS, "ABS", 4);
        lookup[0xce] = new Instruction( "DEC",  this::DEC,  this::ABS, "ABS", 6);
        lookup[0xcf] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 6);
        lookup[0xd0] = new Instruction( "BNE",  this::BNE,  this::REL, "REL", 2);
        lookup[0xd1] = new Instruction( "CMP",  this::CMP,  this::IZY, "IZY", 5);
        lookup[0xd2] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 2);
        lookup[0xd3] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 8);
        lookup[0xd4] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 4);
        lookup[0xd5] = new Instruction( "CMP",  this::CMP,  this::ZPX, "ZPX", 4);
        lookup[0xd6] = new Instruction( "DEC",  this::DEC,  this::ZPX, "ZPX", 6);
        lookup[0xd7] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 6);
        lookup[0xd8] = new Instruction( "CLD",  this::CLD,  this::IMP, "IMP", 2);
        lookup[0xd9] = new Instruction( "CMP",  this::CMP,  this::ABY, "ABY", 4);
        lookup[0xda] = new Instruction( "NOP",  this::NOP,  this::IMP, "IMP", 2);
        lookup[0xdb] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 7);
        lookup[0xdc] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 4);
        lookup[0xdd] = new Instruction( "CMP",  this::CMP,  this::ABX, "ABX", 4);
        lookup[0xde] = new Instruction( "DEC",  this::DEC,  this::ABX, "ABX", 7);
        lookup[0xdf] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 7);
        lookup[0xe0] = new Instruction( "CPX",  this::CPX,  this::IMM, "IMM", 2);
        lookup[0xe1] = new Instruction( "SBC",  this::SBC,  this::IZX, "IZX", 6);
        lookup[0xe2] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 2);
        lookup[0xe3] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 8);
        lookup[0xe4] = new Instruction( "CPX",  this::CPX,  this::ZP0, "ZP0", 3);
        lookup[0xe5] = new Instruction( "SBC",  this::SBC,  this::ZP0, "ZP0", 3);
        lookup[0xe6] = new Instruction( "INC",  this::INC,  this::ZP0, "ZP0", 5);
        lookup[0xe7] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 5);
        lookup[0xe8] = new Instruction( "INX",  this::INX,  this::IMP, "IMP", 2);
        lookup[0xe9] = new Instruction( "SBC",  this::SBC,  this::IMM, "IMM", 2);
        lookup[0xea] = new Instruction( "NOP",  this::NOP,  this::IMP, "IMP", 2);
        lookup[0xeb] = new Instruction( "???",  this::SBC,  this::IMP, "IMP", 2);
        lookup[0xec] = new Instruction( "CPX",  this::CPX,  this::ABS, "ABS", 4);
        lookup[0xed] = new Instruction( "SBC",  this::SBC,  this::ABS, "ABS", 4);
        lookup[0xee] = new Instruction( "INC",  this::INC,  this::ABS, "ABS", 6);
        lookup[0xef] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 6);
        lookup[0xf0] = new Instruction( "BEQ",  this::BEQ,  this::REL, "REL", 2);
        lookup[0xf1] = new Instruction( "SBC",  this::SBC,  this::IZY, "IZY", 5);
        lookup[0xf2] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 2);
        lookup[0xf3] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 8);
        lookup[0xf4] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 4);
        lookup[0xf5] = new Instruction( "SBC",  this::SBC,  this::ZPX, "ZPX", 4);
        lookup[0xf6] = new Instruction( "INC",  this::INC,  this::ZPX, "ZPX", 6);
        lookup[0xf7] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 6);
        lookup[0xf8] = new Instruction( "SED",  this::SED,  this::IMP, "IMP", 2);
        lookup[0xf9] = new Instruction( "SBC",  this::SBC,  this::ABY, "ABY", 4);
        lookup[0xfa] = new Instruction( "NOP",  this::NOP,  this::IMP, "IMP", 2);
        lookup[0xfb] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 7);
        lookup[0xfc] = new Instruction( "???",  this::NOP,  this::IMP, "IMP", 4);
        lookup[0xfd] = new Instruction( "SBC",  this::SBC,  this::ABX, "ABX", 4);
        lookup[0xfe] = new Instruction( "INC",  this::INC,  this::ABX, "ABX", 7);
        lookup[0xff] = new Instruction( "???",  this::XXX,  this::IMP, "IMP", 7);
    }



    public void ConnectBus(Bus bus) {
       this.bus = bus;
    }


    // Produces a map of strings, with keys equivalent to instruction start locations
	// in memory, for the specified address range
	Map<Integer, String> disassemble(int nStart, int nStop){
        int addr = nStart;
        int value = 0x00, lo = 0x00, hi = 0x00;
        int lineAddr;

        Map<Integer, String> mapUtil = new HashMap<>();


        java.util.function.BiFunction<Integer, Integer, String> hex = (n, d) -> {
            StringBuilder s = new StringBuilder(d);
            for (int i = 0; i < d; i++) s.append('0');
            for (int i = d - 1; i >= 0; i--, n >>= 4) 
                s.setCharAt(i, "0123456789ABCDEF".charAt(n & 0xF));
            return s.toString();
        };

        while (addr <= nStop) {
            lineAddr = addr;
            String sInst = "$" + hex.apply(addr, 4) + ": ";
            int opcode = bus.read(addr, true); addr++;
            sInst += lookup[opcode].name + " ";

            String addrmodeName = lookup[opcode].addrmodeName;

            if ("IMP".equals(addrmodeName)) {
                sInst += " {IMP}";
            } else if ("IMM".equals(addrmodeName)) {
                value = bus.read(addr, true); addr++;
                sInst += "#$" + hex.apply(value, 2) + " {IMM}";
            } else if ("ZP0".equals(addrmodeName)) {
                lo = bus.read(addr, true); addr++;
                hi = 0x00;
                sInst += "$" + hex.apply(lo, 2) + " {ZP0}";
            } else if ("ZPX".equals(addrmodeName)) {
                lo = bus.read(addr, true); addr++;
                hi = 0x00;
                sInst += "$" + hex.apply(lo, 2) + ", X {ZPX}";
            } else if ("ZPY".equals(addrmodeName)) {
                lo = bus.read(addr, true); addr++;
                hi = 0x00;
                sInst += "$" + hex.apply(lo, 2) + ", Y {ZPY}";
            } else if ("IZX".equals(addrmodeName)) {
                lo = bus.read(addr, true); addr++;
                hi = 0x00;
                sInst += "($" + hex.apply(lo, 2) + ", X) {IZX}";
            } else if ("IZY".equals(addrmodeName)) {
                lo = bus.read(addr, true); addr++;
                hi = 0x00;
                sInst += "($" + hex.apply(lo, 2) + "), Y {IZY}";
            } else if ("ABS".equals(addrmodeName)) {
                lo = bus.read(addr, true); addr++;
                hi = bus.read(addr, true); addr++;
                sInst += "$" + hex.apply((hi << 8) | lo, 4) + " {ABS}";
            } else if ("ABX".equals(addrmodeName)) {
                lo = bus.read(addr, true); addr++;
                hi = bus.read(addr, true); addr++;
                sInst += "$" + hex.apply((hi << 8) | lo, 4) + ", X {ABX}";
            } else if ("ABY".equals(addrmodeName)) {
                lo = bus.read(addr, true); addr++;
                hi = bus.read(addr, true); addr++;
                sInst += "$" + hex.apply((hi << 8) | lo, 4) + ", Y {ABY}";
            } else if ("IND".equals(addrmodeName)) {
                lo = bus.read(addr, true); addr++;
                hi = bus.read(addr, true); addr++;
                sInst += "($" + hex.apply((hi << 8) | lo, 4) + ") {IND}";
            } else if ("REL".equals(addrmodeName)) {
                value = bus.read(addr, true); addr++;
                sInst += "$" + hex.apply(value, 2) + " [$" + hex.apply(addr + value, 4) + "] {REL}";
            }

            mapUtil.put(lineAddr, sInst);
        }

        return mapUtil;
    }




    ///////////////////////////////////////////////////////////////////////////////
    // BUS CONNECTIVITY
    int read(int a){
        return bus.read(a, false) & 0x00FF;
    }

    void write(int a, int d){
        bus.write(a,d);
    }

    // Convenience functions to access status register
	int GetFlag(FLAGS6502 f){
        return ((status & f.getValue()) > 0) ? 1 : 0;
    }
	
    
    void SetFlag(FLAGS6502 f, boolean v){
        if (v){
            status |= f.getValue();

        }else{
            status &= ~f.getValue();
        }

    }


    // The read location of data can come from two sources, a memory address, or
	// its immediately available as part of the instruction. This function decides
	// depending on address mode of instruction byte
	int fetch(){
        if (lookup[opcode].addrmodeName != "IMP")
            fetched = read(addr_abs);
        return fetched;
    }

    ///////////////////////////////////////////////////////////////////////////////
    // ADDRESSING MODES
    int IMP(){
        fetched = a & 0x00FF;
        return 0;
    }
    int IMM(){
        addr_abs = pc++;
        
        pc &= 0xFFFF;
        return 0;
    }
    int ZP0(){
        addr_abs = read(pc++);
        //addr_abs &= 0x00FF; ??
        pc &= 0xFFFF;
        return 0;
    }
    int ZPX(){
        addr_abs = read(pc++) + x;
        addr_abs &= 0x00FF;
        pc &= 0xFFFF;

        return 0;
    }
    int ZPY(){        
        addr_abs = read(pc++) + y;
        addr_abs &= 0x00FF;
        pc &= 0xFFFF;

        return 0;}
    int REL(){
        addr_rel = read(pc++);
        pc &= 0xFFFF;

        if ((addr_rel & 0x80) == 0x80) addr_rel |= 0xFF00;

        return 0;
    }
    int ABS(){
        int low = read(pc++);
        pc &= 0xFFFF;
        int high = read(pc++);
        pc &= 0xFFFF;

        addr_abs = (high << 8) | low;

        return 0;
    }
    int ABX(){
        int low = read(pc++);
        pc &= 0xFFFF;
        int high = read(pc++);
        pc &= 0xFFFF;

        addr_abs = ((high << 8) | low) + x;
        addr_abs &= 0xFFFF;
        
        if ((addr_abs & 0xFF00) != (high << 8)) return 1;

        return 0;
    }
    int ABY(){
        int low = read(pc++);
        pc &= 0xFFFF;
        int high = read(pc++);
        pc &= 0xFFFF;

        addr_abs = ((high << 8) | low) + y;
        addr_abs &= 0xFFFF;
        
        if ((addr_abs & 0xFF00) != (high << 8)) return 1;

        return 0;
    }
    int IND(){
        int low = read(pc++);
        pc &= 0xFFFF;
        int high = read(pc++);
        pc &= 0xFFFF;

        int ptr = (high << 8) | low;

        if (low == 0x00FF) addr_abs = (read(ptr & 0xFF00) << 8) | read(ptr); //Page boundary bug
        else addr_abs = (read(ptr + 1) << 8) | read(ptr);

        return 0;
    }
    int IZX(){
        int ptr = read(pc++);
        pc &= 0xFFFF;

        int low = read((ptr + (x & 0xFFFF) & 0xFFFF) & 0x00FF);
        int high = read(((ptr + (x & 0xFFFF) + 1) & 0xFFFF) & 0x00FF);

        addr_abs = (high << 8) | low;

        return 0;}
    int IZY(){
        int ptr = read(pc++);
        pc &= 0xFFFF;

        int low = read(ptr & 0x00FF);
        int high = read((ptr + 1) & 0x00FF);

        addr_abs = (high << 8) | low;
        addr_abs += y;
        addr_abs &= 0xFFFF;


        if ((addr_abs & 0xFF00) != (high << 8)) return 1;

        return 0;
    }
    


    ///////////////////////////////////////////////////////////////////////////////
    // INSTRUCTION IMPLEMENTATIONS
    int ADC() {
        fetch();
        temp = (a + fetched + GetFlag(FLAGS6502.C));

        SetFlag(FLAGS6502.C, temp > 0xFF);
        SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0);
        
        
        SetFlag(FLAGS6502.V,  ((~(a ^ fetched) & (a ^ temp)) & 0x0080)!=0 );
        
        SetFlag(FLAGS6502.N, (temp & 0x80) == 0x80);
        

        a = temp & 0x00FF;

        return 1;
        
    }
    int AND() {
        fetch();
        a = a & fetched;
        a &= 0x00FF;

        SetFlag(FLAGS6502.Z, a == 0x00);
        SetFlag(FLAGS6502.N, (a & 0x80) != 0x00);

        return 1;
    }
    int ASL() {
        fetch();
        temp = fetched << 1;

        SetFlag(FLAGS6502.C, (temp & 0xFF00) > 0);
        SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0x00);
        SetFlag(FLAGS6502.N, (temp & 0x80) == 0x80);

        if (lookup[opcode].addrmodeName == "IMP") a = temp & 0x00FF;
        else write(addr_abs, temp & 0x00FF);

        return 0;
    }
    int BCC() {
        if (GetFlag(FLAGS6502.C)==0) {
            cycles++;
            addr_abs = pc + addr_rel;
            addr_abs &= 0xFFFF;
            if ((addr_abs & 0xFF00) != (pc & 0xFF00))
                cycles++;
            pc = addr_abs;
        }
        return 0;
    }
    int BCS(){
        if (GetFlag(FLAGS6502.C)==1 ) {
            cycles++;
            addr_abs = pc + addr_rel;
            addr_abs &= 0xFFFF;
            if ((addr_abs & 0xFF00) != (pc & 0xFF00))
                cycles++;
            pc = addr_abs;
        }
        return 0;
    }	
    int BEQ() {
        if (GetFlag(FLAGS6502.Z)==1) {
            cycles++;
            addr_abs = pc + addr_rel;
            addr_abs &= 0xFFFF;
            if ((addr_abs & 0xFF00) != (pc & 0xFF00))
                cycles++;
            pc = addr_abs;
        }
        return 0;
    }
    int BIT(){
        fetch();
        temp = (a & fetched);

        SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0x0000);
        SetFlag(FLAGS6502.N, (fetched & 0x80) == 0x80);
        SetFlag(FLAGS6502.V, (fetched & 0x40) == 0x40);

        return 0;}	
    int BMI(){
        if (GetFlag(FLAGS6502.N)==1) {
            cycles++;
            addr_abs = pc + addr_rel;
            addr_abs &= 0xFFFF;
            if ((addr_abs & 0xFF00) != (pc & 0xFF00))
                cycles++;
            pc = addr_abs;
        }
        return 0;
    }
    int BNE(){
        if (GetFlag(FLAGS6502.Z)==0) {
            cycles++;
            addr_abs = pc + addr_rel;
            addr_abs &= 0xFFFF;
            if ((addr_abs & 0xFF00) != (pc & 0xFF00))
                cycles++;
            pc = addr_abs;
        }
        return 0;
    }	
    int BPL(){
        if (GetFlag(FLAGS6502.N)==0) {
            cycles++;
            addr_abs = pc + addr_rel;
            addr_abs &= 0xFFFF;
            if ((addr_abs & 0xFF00) != (pc & 0xFF00))
                cycles++;
            pc = addr_abs;
        }
        return 0;
    }	
    int BRK(){
        //Dummy Read
        read((pc - 1) & 0xFFFF);
        pushStack((pc >> 8) & 0x00FF);
        pushStack(pc & 0x00FF);
        pushStack((status | FLAGS6502.B.getValue()) & 0x00FF);
        pc = irqVector();

        SetFlag(FLAGS6502.I, true);

        return 0;
    }	
    int BVC(){
        if (GetFlag(FLAGS6502.V)==0) {
            cycles++;
            addr_abs = pc + addr_rel;
            addr_abs &= 0xFFFF;
            if ((addr_abs & 0xFF00) != (pc & 0xFF00))
                cycles++;
            pc = addr_abs;
        }
        return 0;
    }
    int BVS(){
        if (GetFlag(FLAGS6502.V)==1) {
            cycles++;
            addr_abs = pc + addr_rel;
            addr_abs &= 0xFFFF;
            if ((addr_abs & 0xFF00) != (pc & 0xFF00))
                cycles++;
            pc = addr_abs;
        }
        return 0;
    }	
    int CLC(){
        SetFlag(FLAGS6502.C, false);
        return 0;
    }	
    int CLD(){
        SetFlag(FLAGS6502.D, false);
        return 0;
    }	
    int CLI(){
        SetFlag(FLAGS6502.I, false);
        return 0;
    }
    int CLV(){
        SetFlag(FLAGS6502.V, false);
        return 0;
    }	
    int CMP(){
        fetch();
        temp = a - fetched;

        SetFlag(FLAGS6502.C, a >= fetched);
        SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0x00);
        SetFlag(FLAGS6502.N, (temp & 0x0080) == 0x0080);

        return 1;
    }	
    int CPX(){
        fetch();
        temp = x - fetched;

        SetFlag(FLAGS6502.C, x >= fetched);
        SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0x00);
        SetFlag(FLAGS6502.N, (temp & 0x0080) == 0x0080);

        return 0;
    }	
    int CPY(){
        fetch();
        temp = y - fetched;

        SetFlag(FLAGS6502.C, y >= fetched);
        SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0x00);
        SetFlag(FLAGS6502.N, (temp & 0x0080) == 0x0080);

        return 0;
    }
    int DEC(){
        fetch();
        temp = (fetched - 1) & 0x00FF;
        write(addr_abs, temp);

        SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0x00);
        SetFlag(FLAGS6502.N, (temp & 0x0080) == 0x0080);

        return 0;
    }	
    int DEX(){
        x--;
        x &= 0x00FF;

        SetFlag(FLAGS6502.Z, (x & 0x00FF) == 0x00);
        SetFlag(FLAGS6502.N, (x & 0x80) == 0x80);

        return 0;
    }	
    int DEY(){
        y--;
        y &= 0x00FF;

        SetFlag(FLAGS6502.Z, (y & 0x00FF) == 0x00);
        SetFlag(FLAGS6502.N, (y & 0x80) == 0x80);

        return 0;
    }	
    int EOR(){
        fetch();
        a = (a ^ fetched) & 0x00FF;

        SetFlag(FLAGS6502.Z, (a & 0x00FF) == 0x00);
        SetFlag(FLAGS6502.N, (a & 0x80) == 0x80);

        return 1;
    }
    int INC(){
        fetch();
        temp = (fetched + 1) & 0xFFFF;
        write(addr_abs, temp);

        SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0x0000);
        SetFlag(FLAGS6502.N, (temp & 0x0080) == 0x0080);

        return 0;
    }	
    int INX(){
        x++;
        x &= 0x00FF;

        SetFlag(FLAGS6502.Z, (x & 0x00FF) == 0x00);
        SetFlag(FLAGS6502.N, (x & 0x80) == 0x80);

        return 0;
    }	
    int INY(){
        y++;
        y &= 0x00FF;

        SetFlag(FLAGS6502.Z, (y & 0x00FF) == 0x00);
        SetFlag(FLAGS6502.N, (y & 0x80) == 0x80);

        return 0;
    }	
    int JMP(){
        pc = addr_abs & 0xFFFF;
        return 0;
    }
    int JSR(){
        pc--;
        pc &= 0xFFFF;

        pushStack((pc >> 8) & 0x00FF);
        pushStack(pc & 0x00FF);
        pc = addr_abs & 0xFFFF;

        return 0;
    }	
    int LDA(){
        fetch();
        a = fetched & 0x00FF;

        SetFlag(FLAGS6502.Z, (a & 0x00FF) == 0x00);
        SetFlag(FLAGS6502.N, (a & 0x80) == 0x80);

        return 1;
    }	
    int LDX(){
        fetch();
        x = fetched & 0x00FF;

        SetFlag(FLAGS6502.Z, (x & 0x00FF) == 0x00);
        SetFlag(FLAGS6502.N, (x & 0x80) == 0x80);

        return 1;
    }	
    int LDY(){
        fetch();
        y = fetched & 0x00FF;

        SetFlag(FLAGS6502.Z, (y & 0x00FF) == 0x00);
        SetFlag(FLAGS6502.N, (y & 0x80) == 0x80);

        return 1;
    }
    int LSR(){
        fetch();
        SetFlag(FLAGS6502.C, (fetched & 0x0001) == 0x0001);
        temp = (fetched >> 1);

        SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0x0000);
        SetFlag(FLAGS6502.N, (temp & 0x0080) == 0x0080);
        
        if (lookup[opcode].addrmodeName == "IMP") a = temp & 0x00FF;
        else write(addr_abs, temp & 0x00FF);

        return 0;
    }	
    int NOP(){
        return switch (opcode) {
            case 0x1C, 0x3C, 0x5C, 0x7C, 0xDC, 0xFC -> 1;
            default -> 0;
        };
    }	
    int ORA(){
        fetch();
        a = (a | fetched) & 0x00FF;

        SetFlag(FLAGS6502.Z, a == 0x00);
        SetFlag(FLAGS6502.N, (a & 0x80) == 0x80);

        return 1;
    }	
    int PHA(){
        pushStack(a);
        return 0;
    }
    int PHP(){
        pushStack((status | FLAGS6502.U.getValue() | FLAGS6502.B.getValue()) & 0x00FF);

        SetFlag(FLAGS6502.B, false);
        SetFlag(FLAGS6502.U, false);

        return 0;
    }	
    int PLA(){
        a = popStack();

        SetFlag(FLAGS6502.Z, a == 0x00);
        SetFlag(FLAGS6502.N, (a & 0x80) == 0x80);

        return 0;
    }	
    int PLP(){
        status = popStack();

        SetFlag(FLAGS6502.U, true);

        return 0;
    }	
    int ROL(){
        fetch();
        temp = (GetFlag(FLAGS6502.C)) | (fetched << 1);

        SetFlag(FLAGS6502.C, (temp & 0xFF00) != 0x0000);
        SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0x00);
        SetFlag(FLAGS6502.N, (temp & 0x0080) == 0x0080);

        if (lookup[opcode].addrmodeName == "IMP") a = temp & 0x00FF;
        else write(addr_abs, temp & 0x00FF);

        return 0;
    }
    int ROR(){
        fetch();
        temp = ((GetFlag(FLAGS6502.C) << 7 ) | fetched >> 1);

        SetFlag(FLAGS6502.C, (fetched & 0x01) == 0x01);
        SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0x00);
        SetFlag(FLAGS6502.N, (temp & 0x0080) == 0x0080);

        if (lookup[opcode].addrmodeName == "IMP") a = temp & 0x00FF;
        else write(addr_abs, temp & 0x00FF);

        return 0;
    }	
    int RTI(){
        //Dummy read
        read(pc);
        status = popStack();
        pc = popStack();
        pc |= popStack() << 8;
        pc &= 0xFFFF;

        status &= ~FLAGS6502.B.getValue() & 0x00FF;
        status &= ~FLAGS6502.U.getValue() & 0x00FF;

        return 0;
    }	
    int RTS(){
        //Dummy read
        read(pc);
        pc = popStack();
        pc |= popStack() << 8;
        pc++;
        pc &= 0xFFFF;

        return 0;
    }	
    int SBC(){
        fetch();
        int complement = (fetched ^ 0x00FF);
        temp =a + complement + GetFlag(FLAGS6502.C);
        temp&=0xFFFF;

        SetFlag(FLAGS6502.C, (temp & 0xFF00) == 0xFF00);
        SetFlag(FLAGS6502.Z, (temp & 0x00FF) == 0);
        SetFlag(FLAGS6502.N, (temp & 0x0080) == 0x0080);
        SetFlag(FLAGS6502.V, ((temp ^ a) & (temp ^ complement) & 0x0080) == 0x0080);

        a = (temp & 0x00FF);

        return 1;
    }
    int SEC(){
        SetFlag(FLAGS6502.C, true);
        return 0;
    }	
    int SED(){
        SetFlag(FLAGS6502.D, true);
        return 0;
    }	
    int SEI(){
        SetFlag(FLAGS6502.I, true);
        return 0;
    }	
    int STA(){
        write(addr_abs, a);
        return 0;
    }
    int STX(){
        System.out.println(addr_abs+" stx"+x);
        write(addr_abs, x);
        return 0;
    }	
    int STY(){
        write(addr_abs, y);
        return 0;
    }	
    int TAX(){
        x = a;
        x &= 0x00FF;

        SetFlag(FLAGS6502.Z, x == 0x00);
        SetFlag(FLAGS6502.N, (x & 0x80) == 0x80);

        return 0;
    }	
    int TAY(){
        y = a;
        y &= 0x00FF;

        SetFlag(FLAGS6502.Z, y == 0x00);
        SetFlag(FLAGS6502.N, (y & 0x80) == 0x80);

        return 0;
    }
    int TSX(){
        x = stkp;
        x &= 0x00FF;

        SetFlag(FLAGS6502.Z, x == 0x00);
        SetFlag(FLAGS6502.N, (x & 0x80) == 0x80);

        return 0;
    }	
    int TXA(){
        a = x;
        a &= 0x00FF;

        SetFlag(FLAGS6502.Z, a == 0x00);
        SetFlag(FLAGS6502.N, (a & 0x80) == 0x80);

        return 0;
    }	
    int TXS(){
        stkp = x;
        stkp &= 0x00FF;

        return 0;
    }	
    int TYA(){
        a = y;
        a &= 0x00FF;

        SetFlag(FLAGS6502.Z, a == 0x00);
        SetFlag(FLAGS6502.N, (a & 0x80) == 0x80);

        return 0;
    }
    int XXX(){return 0;}
    


    //Utils
    
    //Return the Non Maskable Interrupt Vector stored at (0xFFFA-0xFFFB)
    int ResetVector(){
        int low = read(0xFFFC);
        int high = read(0xFFFD);
        return (high << 8 | low);
    }

    int irqVector(){
        //Return the Interrupt Vector stored at (0xFFFE-0xFFFF)
        int low = read(0xFFFE);
        int high = read(0xFFFF);
        return (high << 8 | low);
    }

    //Return the Non Maskable Interrupt Vector stored at (0xFFFA-0xFFFB)
    private int nmiVector() {
        int low = read(0xFFFA);
        int high = read(0xFFFB);
        return (high << 8 | low);
    }


    //Update the Stack Pointer accordingly
    private void pushStack(int data) {
        write(0x0100 + stkp, data);
        stkp--;
        stkp &= 0x00FF;
    }

    private int popStack() {
        stkp++;
        stkp &= 0x00FF;
        return read(0x0100 + stkp);
    }

}
